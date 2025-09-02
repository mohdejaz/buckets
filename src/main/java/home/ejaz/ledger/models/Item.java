package home.ejaz.ledger.models;

import java.util.Date;

public record Item(
        Long id,
        Long trnsId,
        String name,
        String merchant,
        String brand,
        Double price,
        Double qty,
        Date createDt) {
}
