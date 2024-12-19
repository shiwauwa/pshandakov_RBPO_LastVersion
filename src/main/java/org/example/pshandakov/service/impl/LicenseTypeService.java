package org.example.pshandakov.service.impl;

import org.example.pshandakov.model.LicenseType;
import org.example.pshandakov.repository.LicenseTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LicenseTypeService {

    private final LicenseTypeRepository licenseTypeRepository;

    @Autowired
    public LicenseTypeService(LicenseTypeRepository licenseTypeRepository) {
        this.licenseTypeRepository = licenseTypeRepository;
    }


    public LicenseType saveLicenseType(LicenseType licenseType) {
        return licenseTypeRepository.save(licenseType);
    }


    public Optional<LicenseType> getLicenseTypeById(Long id) {
        return licenseTypeRepository.findById(id);
    }


    public List<LicenseType> getAllLicenseTypes() {
        return licenseTypeRepository.findAll();
    }


    public void deleteLicenseType(Long id) {
        licenseTypeRepository.deleteById(id);
    }
}
