package com.pingme.contactcrawler.service;

import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContactInfoWriterService {

    private final ContactInfoRepository repository;

    public ContactInfoWriterService(ContactInfoRepository repository) {
        this.repository = repository;
    }

    // Транзакция на пакетной записи в БД
    @Transactional
    public void saveBatch(List<ContactInfo> batch) {
        repository.saveAll(batch);
    }
}
