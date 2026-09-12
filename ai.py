"""Optional AI helpers.

Everything here is inert unless OPENAI_API_KEY is set: `enabled()` returns
False, the routes 404, and the UI never renders the upload box. The app has no
hard dependency on the `openai` package either — the import is guarded so a
source checkout that skipped `pip install -r requirements.txt` still boots.

Two rules this module exists to enforce:

  1. The model reads, it does not calculate. It returns line items exactly as
     printed on the receipt; every sum, tax split, and balance check is done
     in Python below. A plausible-looking wrong total is worse than no feature
     at all in a budgeting app.
  2. Nothing here writes to the database. Parsing returns a proposal; the user
     confirms it and a separate route commits it.
"""

import base64
import io
import json
import os

# Both imports are optional. Missing either one degrades to "AI is off"
# rather than breaking the app.
try:
    from openai import OpenAI, APITimeoutError
except ImportError:
    OpenAI = None
    APITimeoutError = ()

try:
    from PIL import Image
except ImportError:
    Image = None

# Optional: iPhone photos are HEIC, which the API does not accept. iOS Safari
# usually converts on upload, but a desktop drag-and-drop of a .heic file will
# not. Installing pillow-heif makes those work; without it they are rejected
# with a clear message rather than a confusing API error.
try:
    import pillow_heif
    pillow_heif.register_heif_opener()
    HEIC_SUPPORTED = True
except ImportError:
    HEIC_SUPPORTED = False


API_KEY = os.environ.get('OPENAI_API_KEY', '').strip()

# Overridable so a self-hoster can point at any OpenAI-compatible endpoint
# (Ollama, llama.cpp, LiteLLM) and keep receipt images on their own machine.
BASE_URL = os.environ.get('OPENAI_BASE_URL', '').strip() or None
MODEL = os.environ.get('BUCKETS_AI_MODEL', '').strip() or 'gpt-4o-mini'

# Must stay comfortably under the gunicorn worker timeout (300s by default, see
# run.sh / Dockerfile), including retries, so a slow model returns a readable
# error instead of a worker killed mid-request.
try:
    REQUEST_TIMEOUT = float(os.environ.get('BUCKETS_AI_TIMEOUT', '120'))
except ValueError:
    REQUEST_TIMEOUT = 120.0

# Receipt photos come off phones at 3-4 MB. Vision cost scales with pixels and
# a receipt is legible well below full resolution, so downscale before sending.
MAX_EDGE = 1600
JPEG_QUALITY = 80
MAX_UPLOAD_BYTES = 12 * 1024 * 1024
MAX_IMAGES = 5


def enabled():
    """True when receipt parsing can actually run."""
    return bool(API_KEY) and OpenAI is not None and Image is not None


def unavailable_reason():
    """Why AI is off, for the status endpoint. None when it's on."""
    if not API_KEY:
        return 'OPENAI_API_KEY is not set'
    if OpenAI is None:
        return "the 'openai' package is not installed"
    if Image is None:
        return "the 'Pillow' package is not installed"
    return None


# ---------------------------------------------------------------------------
# Money helpers — all arithmetic lives here, never in the model
# ---------------------------------------------------------------------------

def _cents(value):
    """Currency as integer cents. Float dollars do not survive summing."""
    try:
        return int(round(float(value) * 100))
    except (TypeError, ValueError):
        return 0


# Receipts mark taxable lines with a code printed beside the price. Conventions
# vary by chain, but the exempt markers are a small, stable set — "E"xempt,
# "F"ood, "N"ontaxable, "0". Anything else non-empty is treated as taxable.
# This reads the paper rather than asking the model to judge taxability, which
# it would get wrong on exactly the items that matter.
EXEMPT_TAX_CODES = {'E', 'F', 'N', 'O', '0'}


def is_taxable(tax_code):
    code = (tax_code or '').strip().upper()
    return bool(code) and code not in EXEMPT_TAX_CODES


# ---------------------------------------------------------------------------
# Image preparation
# ---------------------------------------------------------------------------

def prepare_image(raw, filename=''):
    """Downscale to a JPEG data URL. Raises ValueError with a usable message."""
    if len(raw) > MAX_UPLOAD_BYTES:
        raise ValueError(f'{filename or "Image"} is larger than 12 MB.')
    try:
        img = Image.open(io.BytesIO(raw))
        img.load()
    except Exception:
        if filename.lower().endswith(('.heic', '.heif')) and not HEIC_SUPPORTED:
            raise ValueError(
                f'{filename} is a HEIC image. Install pillow-heif to read '
                'those, or export it as JPEG first.'
            )
        raise ValueError(f'Could not read {filename or "the image"}.')

    # EXIF orientation: a photo taken sideways reads as gibberish otherwise.
    try:
        from PIL import ImageOps
        img = ImageOps.exif_transpose(img)
    except Exception:
        pass

    if img.mode != 'RGB':
        img = img.convert('RGB')
    if max(img.size) > MAX_EDGE:
        ratio = MAX_EDGE / max(img.size)
        img = img.resize(
            (max(1, int(img.width * ratio)), max(1, int(img.height * ratio))),
            Image.LANCZOS,
        )

    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=JPEG_QUALITY, optimize=True)
    b64 = base64.b64encode(buf.getvalue()).decode('ascii')
    return f'data:image/jpeg;base64,{b64}'


# ---------------------------------------------------------------------------
# Receipt parsing
# ---------------------------------------------------------------------------

RECEIPT_SCHEMA = {
    'type': 'object',
    'additionalProperties': False,
    'required': ['merchant', 'purchase_date', 'lines', 'subtotal', 'tax', 'total'],
    'properties': {
        'merchant': {'type': 'string'},
        'purchase_date': {
            'type': 'string',
            'description': 'YYYY-MM-DD, or empty string if not printed.',
        },
        'lines': {
            'type': 'array',
            'items': {
                'type': 'object',
                'additionalProperties': False,
                'required': ['description', 'raw_description', 'amount',
                             'tax_code', 'bucket'],
                'properties': {
                    'description': {
                        'type': 'string',
                        'description': 'Readable name. A store abbreviation may be '
                                       'expanded only when unambiguous; otherwise '
                                       'repeat the original text verbatim.',
                    },
                    'raw_description': {
                        'type': 'string',
                        'description': 'The item text exactly as printed, unexpanded.',
                    },
                    'amount': {
                        'type': 'number',
                        'description': 'Positive for a purchase, negative for a '
                                       'discount, coupon or instant savings line.',
                    },
                    # Recorded for fidelity with the paper only. Tax is entered as
                    # its own row, so nothing here feeds a calculation.
                    'tax_code': {
                        'type': ['string', 'null'],
                        'description': 'Tax marker printed beside the item (e.g. "A", '
                                       '"E"), or null if none is shown.',
                    },
                    'bucket': {
                        'type': 'string',
                        'description': 'Exact bucket name from the provided list, '
                                       'or empty string if genuinely unsure.',
                    },
                },
            },
        },
        'subtotal': {'type': ['number', 'null']},
        'tax': {'type': ['number', 'null']},
        'total': {'type': ['number', 'null']},
    },
}

SYSTEM_PROMPT = """\
You read retail receipts and map each purchased line item to a household budget bucket.

Rules:
- Transcribe every purchased line item visible on the receipt.
- Preserve the original receipt description in `raw_description`.
- You may expand common store abbreviations into a readable `description`,
  but do not invent, guess, or add information not supported by the receipt.
  If the abbreviation cannot be confidently expanded, use the original text.

- Amounts must be exactly as printed.
  Purchases are positive; discounts, coupons, and instant savings are negative.
  Do not apply discounts to item prices yourself.
  Do not calculate net item amounts.

- Preserve any tax code or marker printed with an item exactly as shown
  (for example: "A", "E", or null).
  Do NOT infer taxability from the product name.
  Do NOT calculate or allocate sales tax to individual items.

- Do NOT include subtotal, sales tax, or receipt total as purchased line items.
  Report receipt-level amounts in their own fields exactly as printed.

- Never compute, infer, or reconcile totals.
  Report only information visible on the receipt.

- Assign each purchased item to one of the user's bucket names provided below.
  Use bucket names verbatim.
  If an item does not clearly belong to a bucket, return an empty string.

- If text or a number cannot be read confidently, return null rather than guessing.
"""


def _client():
    # max_retries=1 rather than the SDK default of 2: each retry costs another
    # full timeout, and three of them would outlast the worker.
    return OpenAI(api_key=API_KEY, base_url=BASE_URL,
                  timeout=REQUEST_TIMEOUT, max_retries=1)


# Reasoning-family models reject an explicit temperature and accept only their
# default. Rather than keep a list of model names that goes stale every release,
# send temperature=0 (worth having for transcription — it makes the same receipt
# read the same way twice), and if the API rejects it, drop it and remember not
# to send it again for the life of the process.
_SEND_TEMPERATURE = True


def _create(**kwargs):
    global _SEND_TEMPERATURE
    try:
        if _SEND_TEMPERATURE:
            try:
                return _client().chat.completions.create(temperature=0, **kwargs)
            except APITimeoutError:
                raise                       # a timeout says nothing about temperature
            except Exception as e:
                if 'temperature' not in str(e):
                    raise
                _SEND_TEMPERATURE = False
        return _client().chat.completions.create(**kwargs)
    except APITimeoutError:
        raise RuntimeError(
            f'The model took longer than {REQUEST_TIMEOUT:.0f}s to read the '
            'receipt. Try one clearer photo instead of several, or set '
            'BUCKETS_AI_MODEL to a faster model — reasoning models are slow '
            'at transcription.'
        )
    except Exception as e:
        # A project's allowed-model list takes a while to propagate after it is
        # changed, and during that window identical requests alternate between
        # working and 403. Say so, because the raw error reads like a hard
        # permissions failure and sends you looking in the wrong place.
        if 'model_not_found' in str(e) or 'does not have access to model' in str(e):
            raise RuntimeError(
                f'OpenAI rejected the model {MODEL!r} for this project. If you '
                'just changed the project\'s allowed models, that setting takes '
                'a few minutes to propagate and this will start working on its '
                'own — try again shortly. Otherwise set BUCKETS_AI_MODEL to a '
                'model the project is allowed to use.'
            )
        raise


def parse_receipt(images, bucket_names, examples=None):
    """Parse receipt images into line items with suggested buckets.

    `images` is a list of (bytes, filename). `examples` is a list of
    (note, bucket_name) pairs from the user's own history, which is what
    teaches the model that a given cryptic vendor string means Groceries
    *for this user* rather than in general.

    Returns the raw proposal. No arithmetic has been applied yet.
    """
    if not enabled():
        raise RuntimeError('AI features are not configured.')
    if not images:
        raise ValueError('No image supplied.')
    if len(images) > MAX_IMAGES:
        raise ValueError(f'At most {MAX_IMAGES} images per receipt.')

    context = ['Available bucket names:'] + [f'- {n}' for n in bucket_names]
    if examples:
        context.append('')
        context.append("Previous categorisations by this user (follow these patterns):")
        for note, bucket in examples[:40]:
            context.append(f'- "{note}" -> {bucket}')

    content = [{'type': 'text', 'text': '\n'.join(context)}]
    for raw, filename in images:
        content.append({
            'type': 'image_url',
            'image_url': {'url': prepare_image(raw, filename)},
        })

    resp = _create(
        model=MODEL,
        messages=[
            {'role': 'system', 'content': SYSTEM_PROMPT},
            {'role': 'user', 'content': content},
        ],
        response_format={
            'type': 'json_schema',
            'json_schema': {
                'name': 'receipt',
                'strict': True,
                'schema': RECEIPT_SCHEMA,
            },
        },
    )
    return json.loads(resp.choices[0].message.content)


def build_proposal(parsed, buckets, today):
    """Turn a raw model response into a reviewable, arithmetic-checked proposal.

    `buckets` maps lowercased bucket name -> {'id', 'name'}. Amounts come back
    negative, matching this app's convention that spending reduces a bucket
    balance. Items keep exactly the price printed on the receipt — tax is
    reported on its own and gets its own row in the review table, rather than
    being spread across items where it would no longer match the paper.
    """
    raw_lines = parsed.get('lines') or []

    lines, item_cents = [], 0
    for item in raw_lines:
        cents = _cents(item.get('amount'))
        if cents == 0:
            continue  # zero-value rows are noise, not purchases
        match = buckets.get((item.get('bucket') or '').strip().lower())
        raw = (item.get('raw_description') or '').strip()
        code = (item.get('tax_code') or '').strip()
        lines.append({
            'description': (item.get('description') or '').strip() or raw or 'Item',
            'raw_description': raw,
            'amount': round(-cents / 100, 2),   # negative: spending
            'tax_code': code,
            'taxable': is_taxable(code),
            'bucket_id': match['id'] if match else None,
        })
        item_cents += cents

    tax_cents = _cents(parsed.get('tax'))
    computed = item_cents + tax_cents
    printed = _cents(parsed.get('total')) if parsed.get('total') is not None else None

    purchase_date = (parsed.get('purchase_date') or '').strip()
    if len(purchase_date) != 10:
        purchase_date = today

    return {
        'merchant': (parsed.get('merchant') or '').strip(),
        'purchase_date': purchase_date,
        'lines': lines,
        'tax': round(-tax_cents / 100, 2),      # negative, like any other charge
        'subtotal': round(-item_cents / 100, 2),
        'computed_total': round(computed / 100, 2),
        'printed_total': round(printed / 100, 2) if printed is not None else None,
        # Non-zero means the transcription missed or misread something. Shown
        # as a warning rather than blocking: the user can fix it in the table.
        'discrepancy': round((computed - printed) / 100, 2) if printed is not None else None,
        'unmatched': sum(1 for l in lines if l['bucket_id'] is None),
        # False means the receipt carried no usable tax markers, so spreading
        # tax can only fall back to "across everything".
        'has_tax_codes': any(l['taxable'] for l in lines),
    }
