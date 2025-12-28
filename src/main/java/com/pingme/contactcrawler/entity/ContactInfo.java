package com.pingme.contactcrawler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "contact_info",
        indexes = {
                @Index(name = "idx_contact_info_name", columnList = "name"),
                @Index(name = "idx_contact_info_website", columnList = "website")
        }
)
public class ContactInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Название организации
    private String name;

    // Сайт (URL)
    private String website;

    // Телефон
    @Column(length = 1000)
    private String phones;

    // Email (может быть null)
    private String email;

    // Адрес
    private String address;

    public ContactInfo() {
    }

    public ContactInfo(String name, String website, String phones, String email, String address) {
        this.name = name;
        this.website = website;
        this.phones = phones;
        this.email = email;
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWebsite() {
        return website;
    }

    public String getPhone() {
        return phones;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public void setPhone(String phones) {
        this.phones = phones;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
