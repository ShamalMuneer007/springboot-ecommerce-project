package com.needus.ecommerce.entity.product;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Categories {
    @OneToMany(cascade = CascadeType.REMOVE,mappedBy = "categories", fetch = FetchType.EAGER)
    List<Products> products;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "categoryId")
    private Long categoryId;
    private String categoryName;
    private Float discountOfferPercentage = 0F;
    private LocalDate discountOfferExpiryDate = null;
    private boolean isDiscountOfferExpired = true;
    private boolean isDeleted = false;
}
