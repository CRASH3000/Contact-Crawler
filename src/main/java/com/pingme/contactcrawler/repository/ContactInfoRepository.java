package com.pingme.contactcrawler.repository;

import com.pingme.contactcrawler.entity.ContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactInfoRepository extends JpaRepository<ContactInfo, Long> {

    // Поиск по названию или сайту (частичное совпадение, без учета регистра)
    List<ContactInfo> findByNameContainingIgnoreCaseOrWebsiteContainingIgnoreCase(String name, String website);
}
