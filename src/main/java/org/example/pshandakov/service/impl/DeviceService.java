package org.example.pshandakov.service.impl;

import org.example.pshandakov.model.Device;
import org.example.pshandakov.model.DeviceLicense;
import org.example.pshandakov.model.License;
import org.example.pshandakov.repository.DeviceLicenseRepository;
import org.example.pshandakov.repository.DeviceRepository;
import org.example.pshandakov.repository.LicenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseRepository licenseRepository;

    @Autowired
    public DeviceService(DeviceRepository deviceRepository,
                         DeviceLicenseRepository deviceLicenseRepository,
                         LicenseRepository licenseRepository) {
        this.deviceRepository = deviceRepository;
        this.deviceLicenseRepository = deviceLicenseRepository;
        this.licenseRepository = licenseRepository;
    }


    public Device saveDevice(Device device) {
        return deviceRepository.save(device);
    }


    public Optional<Device> getDeviceById(Long id) {
        return deviceRepository.findById(id);
    }


    public Device getDeviceByMacAddress(String macAddress) {
        return deviceRepository.findByMacAddress(macAddress);
    }


    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }


    public void deleteDevice(Long id) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);

        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();


            Optional<DeviceLicense> deviceLicenseOpt = deviceLicenseRepository.findByDeviceId(id);
            if (deviceLicenseOpt.isPresent()) {
                DeviceLicense deviceLicense = deviceLicenseOpt.get();

                Optional<License> licenseOpt = licenseRepository.findById(deviceLicense.getLicenseId());


                License license = licenseOpt.get();


                license.setDeviceCount(license.getDeviceCount() + 1);
                licenseRepository.save(license);


                deviceLicenseRepository.delete(deviceLicense);
            }


            deviceRepository.delete(device);
        }
    }
}
