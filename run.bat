@echo off

where python >nul 2>&1
if errorlevel 1 (
    echo Python is not installed or not in PATH.
    pause
    exit /b 1
)

if not exist venv (
    echo Creating virtual environment...
    python -m venv venv
)

call venv\Scripts\activate.bat

pip install -q -r requirements.txt

echo Starting Buckets webapp...
python app.py

pause
