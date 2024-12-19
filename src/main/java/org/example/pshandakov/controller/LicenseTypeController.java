package org.example.pshandakov.controller;

import org.example.pshandakov.model.LicenseType;
import org.example.pshandakov.service.impl.LicenseTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/license-types")
public class LicenseTypeController {

    private final LicenseTypeService licenseTypeService;

    @Autowired
    public LicenseTypeController(LicenseTypeService licenseTypeService) {
        this.licenseTypeService = licenseTypeService;
    }


    @PostMapping
    public ResponseEntity<LicenseType> createOrUpdateLicenseType(@RequestBody LicenseType licenseType) {
        LicenseType savedLicenseType = licenseTypeService.saveLicenseType(licenseType);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedLicenseType);
    }


    @GetMapping("/{id}")
    public ResponseEntity<LicenseType> getLicenseTypeById(@PathVariable Long id) {
        Optional<LicenseType> licenseType = licenseTypeService.getLicenseTypeById(id);
        return licenseType.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }


    @GetMapping
    public ResponseEntity<List<LicenseType>> getAllLicenseTypes() {
        List<LicenseType> licenseTypes = licenseTypeService.getAllLicenseTypes();
        return ResponseEntity.ok(licenseTypes);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLicenseType(@PathVariable Long id) {
        Optional<LicenseType> licenseType = licenseTypeService.getLicenseTypeById(id);
        if (licenseType.isPresent()) {
            licenseTypeService.deleteLicenseType(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
