package org.example.pshandakov.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.pshandakov.model.*;
import org.example.pshandakov.repository.*;
import org.example.pshandakov.service.impl.LicenseHistoryService;
import org.example.pshandakov.configuration.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.example.pshandakov.repository.ActionAuthRegHistoryRepository;
import org.example.pshandakov.repository.ApplicationUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@RequestMapping("/licensing")
@RequiredArgsConstructor
public class LicensingController {

    private final JwtTokenProvider jwtTokenProvider;
    private final ProductRepository productRepository;
    private final ApplicationUserRepository applicationUserRepository;
    private final LicenseTypeRepository licenseTypeRepository;
    private final LicenseRepository licenseRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseHistoryService licenseHistoryService;
    private final DeviceRepository deviceRepository;
    private final ApplicationUserRepository userRepository;
    private final ActionAuthRegHistoryRepository actionAuthRegHistoryRepository;


    private Date convertLocalDateToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }


    @PostMapping("/create")
    public ResponseEntity<?> createLicense(HttpServletRequest request, @RequestBody LicenseCreateRequest requestData) {
        Logger logger = LoggerFactory.getLogger(getClass());
        String email = jwtTokenProvider.getEmailFromRequest(request);
        Optional<ApplicationUser> userOptional = applicationUserRepository.findByEmail(email);
        ApplicationUser user = userOptional.get();

        try {

            logger.info("Извлечение роли из токена...");
            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);


            if (!roles.contains("ROLE_ADMIN")) {
                logger.warn("Попытка создать лицензию без прав ADMIN. Роль: {}", roles);
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Попытка создать лицензию без прав ADMIN", "Лицензия");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Нет прав для создания лицензии");

            }


            logger.info("Проверка существования продукта с ID: {}", requestData.getProductId());
            Product product = productRepository.findById(requestData.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Продукт не найден"));
            logger.info("Продукт найден: {}", product.getName());


            if (product.isBlocked()) {
                logger.warn("Продукт с ID: {} заблокирован", requestData.getProductId());
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка создания лицензии: продукт лицензии заблокирован", "Лицензия");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Продукт заблокирован, лицензия не может быть создана");

            }


            logger.info("Проверка существования пользователя с ID: {}", requestData.getOwnerId());
            ApplicationUser owner = applicationUserRepository.findById(requestData.getOwnerId())
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

            logger.info("Пользователь найден: {}", owner.getUsername());


            logger.info("Проверка существования типа лицензии с ID: {}", requestData.getLicenseTypeId());
            LicenseType licenseType = licenseTypeRepository.findById(requestData.getLicenseTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Тип лицензии не найден"));
            logger.info("Тип лицензии найден: {}", licenseType.getName());

            if(requestData.getLicenseTypeId() != null && requestData.getProductId() != null)
            {
                logger.info("Создание новой лицензии...");
                License newLicense = new License();
                newLicense.setCode(generateActivationCode());
                logger.info("Активационный код сгенерирован: {}", newLicense.getCode());


                newLicense.setOwner(owner);
                newLicense.setProduct(product);
                newLicense.setLicenseType(licenseType);


                newLicense.setFirstActivationDate(convertLocalDateToDate(LocalDate.now()));


                int duration = licenseType.getDefaultDuration();
                LocalDate endingLocalDate = LocalDate.now().plusDays(duration);
                newLicense.setEndingDate(convertLocalDateToDate(endingLocalDate));


                newLicense.setBlocked(false);


                newLicense.setDeviceCount(requestData.getDeviceCount());
                newLicense.setDuration(duration);
                newLicense.setDescription(requestData.getDescription() != null ? requestData.getDescription() : "Лицензия успешно создана: " + user.getUsername());


                logger.info("Сохранение лицензии в базе данных...");
                licenseRepository.save(newLicense);
                logger.info("Лицензия успешно сохранена в базе данных с ID: {}", newLicense.getId());


                String description = "Лицензия создана";
                Date changeDate = convertLocalDateToDate(LocalDate.now());
                licenseHistoryService.recordLicenseChange(newLicense.getId(), owner.getId(), "Создана", changeDate, description);
                logger.info("Запись изменений лицензии в историю завершена");
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Лицензия была создана: " + user.getUsername(), "Лицензия");


                return ResponseEntity.status(HttpStatus.CREATED).body("Лицензия успешно создана");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body("Лицензия успешно создана");

        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при создании лицензии: {}", e.getMessage(), e);
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка создания лицензии: " + e.getMessage(), "Лицензия");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Произошла ошибка при создании лицензии: {}", e.getMessage(), e);
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка создания лицензии: " + e.getMessage(), "Лицензия");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при создании лицензии");
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateLicense(HttpServletRequest request, @RequestBody LicenseUpdateRequest requestData)
    {
        Logger logger = LoggerFactory.getLogger(getClass());
        String email = jwtTokenProvider.getEmailFromRequest(request);
        Optional<ApplicationUser> userOptional = applicationUserRepository.findByEmail(email);
        ApplicationUser user = userOptional.get();

        try {

            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);


            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }
            if (user.getUsername().isBlank() && (!roles.contains("ADMIN") || !roles.contains("USER"))) {

                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: попытка подделки токена", "Лицензия");
                logger.error("Ошибка аутентификации: подделка JWT");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");


            }


            License license = licenseRepository.findByCode(requestData.getCode())
                    .orElseThrow(() -> new IllegalArgumentException("Недействительный ключ лицензии"));
            logger.info("Лицензия с кодом {} найдена", requestData.getCode());

            String email1 = jwtTokenProvider.getEmailFromRequest(request);
            Optional<ApplicationUser> userOptional1 = applicationUserRepository.findByEmail(email1);
            ApplicationUser user1 = userOptional1.get();

            if (!user1.getId().equals(license.getOwner().getId())) {
                logger.error("Ошибка: пользователь не является владельцем лицензии");
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: попытка обновления лицензии будучи !owner", "Лицензия");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ошибка: пользователь не является владельцем лицензии");
            }


            if (license.getBlocked()) {
                logger.warn("Лицензия с кодом {} заблокирована", requestData.getCode());

                Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                        true,
                        null);


                logger.info("Тикет: {}", ticket);

                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: попытка обновления заблокированной лицензии", "Лицензия");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия заблокирована, продление невозможно.");
            }

            if (license.getEndingDate().before(new Date())) {
                logger.warn("Лицензия с кодом {} уже истекла", requestData.getCode());

                Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                        false,
                        null);


                logger.info("Тикет: {}", ticket);


                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия истекла, продление невозможно.");
            }


            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date newExpirationDate = sdf.parse(requestData.getNewExpirationDate());

            logger.info("Новая дата окончания: {}", newExpirationDate);


            if (newExpirationDate.compareTo(license.getEndingDate()) <= 0) {
                logger.warn("Новая дата окончания лицензии {} не может быть меньше или равна текущей дате окончания {}",
                        requestData.getNewExpirationDate(), license.getEndingDate());


                Ticket ticket = Ticket.createTicket(
                        license.getOwner().getId(),
                        false,
                        null
                );


                logger.info("Тикет: {}", ticket);


                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Новая дата окончания лицензии не может быть меньше или равна текущей. Тикет с отказом был создан.");
            }

            int newDuration = calculateDaysBetween(newExpirationDate);

            license.setEndingDate(newExpirationDate);
            license.setDuration(newDuration);

            licenseRepository.save(license);
            logger.info("Лицензия с кодом {} продлена до: {}", requestData.getCode(), newExpirationDate);

            Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                    false,
                    newExpirationDate);

            Optional<DeviceLicense> deviceLicenseOpt = deviceLicenseRepository.findByLicenseId(license.getId());
            Date activationDate = null;
            Long deviceId = null;
            String deviceMessage = null;

            if (deviceLicenseOpt.isPresent()) {
                DeviceLicense deviceLicense = deviceLicenseOpt.get();

                activationDate = deviceLicense.getActivationDate();
                deviceId = deviceLicense.getDeviceId();

                deviceMessage = "Лицензия активирована на устройстве";

                ticket.setActivationDate(activationDate);
                ticket.setDeviceId(deviceId);
                ticket.setTicketLifetime(newDuration);

                logger.info("Тикет: {}", ticket);


                return ResponseEntity.status(HttpStatus.OK).body(deviceMessage + "\nЛицензия продлена до: " + newExpirationDate);
            }
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Лицензия успешно продлена", "Лицензия");
            return ResponseEntity.status(HttpStatus.OK).body("Лицензия продлена");




        }
        catch (ParseException e)
        {
            logger.error("Ошибка при парсинге даты: {}", e.getMessage());
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: " + e.getMessage(), "Лицензия");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Неверный формат даты.");
        }
        catch (IllegalArgumentException e)
        {
            logger.error("Ошибка: {}", e.getMessage());

            Ticket ticket = Ticket.createTicket(null,
                    false,
                    null);
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: " + e.getMessage(), "Лицензия");


            logger.info("Тикет: {}", ticket);


            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недействительный ключ лицензии.");

        }
        catch (Exception e)
        {
            logger.error("Произошла ошибка: {}", e.getMessage());

            Ticket ticket = Ticket.createTicket(null,
                    false,
                    null);

            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: " + e.getMessage(), "Лицензия");

            logger.info("Тикет: {}", ticket);


            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при продлении лицензии.");
        }
    }



    public static int calculateDaysBetween(Date expirationDate) {

        LocalDateTime currentDate = LocalDateTime.now();
        LocalDate expirationLocalDate = expirationDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();


        long daysBetween = ChronoUnit.DAYS.between(currentDate.toLocalDate(), expirationLocalDate);

        return (int) daysBetween;
    }


    @PostMapping("/check")
    public ResponseEntity<?> checkLicense(HttpServletRequest request, @RequestBody LicenseCheckRequest requestData) {
        Logger logger = LoggerFactory.getLogger(getClass());
        String email = jwtTokenProvider.getEmailFromRequest(request);
        Optional<ApplicationUser> userOptional = applicationUserRepository.findByEmail(email);
        ApplicationUser user = userOptional.get();

        try {

            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);

            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }
            if(user.getUsername().isBlank() && (!roles.contains("ADMIN") || !roles.contains("USER")))
            {

                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка обновления лицензии: попытка подделки токена", "Лицензия");
                logger.error("Ошибка аутентификации: подделка JWT");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");


            }


            Optional<Device> deviceOptional = deviceRepository.findByMacAddressAndName(requestData.getMacAddress(), requestData.getDeviceName());
            if (!deviceOptional.isPresent()) {
                logger.error("Ошибка: устройство не найдено с MAC-адресом {} и именем {}", requestData.getMacAddress(), requestData.getDeviceName());
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка проверки лицензии: устройство не найдено", "Лицензия");

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Устройство не найдено");
            }
            Device device = deviceOptional.get();
            logger.info("Устройство найдено: {}", device);


            Optional<DeviceLicense> deviceLicenseOptional = deviceLicenseRepository.findByDeviceId(device.getId());

            if (!deviceLicenseOptional.isPresent()) {
                logger.warn("Лицензия не найдена для устройства с ID {}", device.getId());
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка проверки лицензии: лицензия не найдена с ID: " + device.getId(), "Лицензия");

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Лицензия для устройства не найдена");
            }


            DeviceLicense deviceLicense = deviceLicenseOptional.get();


            Optional<License> licenseOptional = licenseRepository.findById(deviceLicense.getLicenseId());

            if (licenseOptional.isPresent()) {
                License license = licenseOptional.get();

                Ticket ticket = Ticket.createTicket(license.getUser().getId(), false, license.getEndingDate());
                ticket.setDeviceId(deviceLicense.getDeviceId());

                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Успешная проверка лицензии", "Лицензия");


                logger.info("Тикет с подтверждением лицензии: {}", ticket);


                return ResponseEntity.status(HttpStatus.OK).body("Лицензия активирована на устройстве. " + deviceLicense.getLicenseId());

            } else {

                logger.error("Лицензия с ID {} не найдена", deviceLicense.getLicenseId());

                Ticket ticket = Ticket.createTicket(null, true, null);
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка проверки лицензии: попытка найти несуществующую лицензию", "Лицензия");

                return ResponseEntity.status(HttpStatus.OK).body("Лицензия не найдена. Тикет: " + ticket);
            }

        } catch (Exception e) {
            logger.error("Произошла ошибка при проверке лицензии: {}", e.getMessage());
            Ticket ticket = Ticket.createTicket(null, false, null);  // Ошибка без данных лицензии
            logger.info("Тикет с ошибкой: {}", ticket);
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка проверки лицензии: " + e.getMessage(), "Лицензия");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при проверке лицензии.");
        }
    }


    @PostMapping("/activation")
    public ResponseEntity<?> activateLicense(HttpServletRequest request, @RequestBody LicenseActivationRequest activationRequest) {
        Logger logger = LoggerFactory.getLogger(getClass());
        String email = jwtTokenProvider.getEmailFromRequest(request);
        Optional<ApplicationUser> userOptional = applicationUserRepository.findByEmail(email);
        ApplicationUser user = userOptional.get();

        try {

            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);

            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }
            if(user.getUsername().isBlank() && (!roles.contains("ADMIN") || !roles.contains("USER")))
            {

                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: попытка подделки токена", "Лицензия");
                logger.error("Ошибка аутентификации: подделка JWT");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");


            }

            Optional<License> licenseOptional = licenseRepository.findByCode(activationRequest.getCode());
            License license = licenseOptional.get();




            if (!licenseOptional.isPresent()) {
                logger.error("Лицензия с кодом {} не найдена", activationRequest.getCode());
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: лицензия не найдена", "Лицензия");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия не найдена");
            }



            if (license.getUser() != null) {
                if (!license.getUser().getEmail().equals(email)) {
                    logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: некорректный email", "Лицензия");

                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ошибка");
                }
            } else {
                license.setUser(user);
            }


            Optional<Device> deviceOptional = deviceRepository.findByMacAddressAndName(activationRequest.getMacAddress(), activationRequest.getDeviceName());
            Device device;

            Optional<Device> existingDevice = deviceRepository.findByMacAddressAndName(activationRequest.getMacAddress(), activationRequest.getDeviceName());
            if (existingDevice.isPresent()) {

                if(isMacAddressExists(activationRequest.getMacAddress()) && isDeviceExists(activationRequest.getMacAddress(), activationRequest.getDeviceName()))
                {
                    logger.error("Устройство с MAC-адресом {} и именем {} уже существует", activationRequest.getMacAddress(), activationRequest.getDeviceName());
                    logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: существующий MAC-address и deviceName", "Лицензия");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Устройство с таким MAC-адресом и именем уже существует");
                }
                else if(isMacAddressExists(activationRequest.getMacAddress()))
                {
                    logger.error("Устройство с MAC-адресом {} уже существует", activationRequest.getMacAddress());
                    logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: существующий MAC-address", "Лицензия");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Устройство с таким MAC-адресом уже существует");
                }
            }

            if (deviceOptional.isPresent()) {
                device = deviceOptional.get();
                logger.info("Устройство найдено: {}", device);

            } else {
                device = new Device();
                device.setMacAddress(activationRequest.getMacAddress());
                if(isMacAddressExists(activationRequest.getMacAddress()))
                {
                    logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: существующий MAC-address", "Лицензия");

                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Устройство с таким MAC-адресом уже существует");
                }
                device.setName(activationRequest.getDeviceName());
                device.setUserId(license.getUser().getId());
                deviceRepository.save(device);
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Добавление новых устройств в лицензию", "Лицензия");
                logger.info("Устройство с MAC-адресом {} и именем {} зарегистрировано", activationRequest.getMacAddress(), activationRequest.getDeviceName());
            }


            if (license.getDeviceCount() <= 0) {
                logger.warn("Для лицензии с кодом {} нет доступных мест для активации", activationRequest.getCode());
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: превышен лимит количества устройств на лицензии", "Лицензия");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Превышен лимит количества устройств на лицензии");
            }


            Optional<DeviceLicense> existingDeviceLicenseOptional = deviceLicenseRepository.findByDeviceIdAndLicenseId(device.getId(), license.getId());

            if (existingDeviceLicenseOptional.isPresent()) {
                logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации лицензии: уже имеется активная лицензия на данном устройстве", "Лицензия");

                logger.warn("Лицензия {} уже активирована на устройстве с ID {}", activationRequest.getCode(), device.getId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия уже активирована на данном устройстве");
            }


            DeviceLicense deviceLicense = new DeviceLicense();
            deviceLicense.setLicenseId(license.getId());
            deviceLicense.setDeviceId(device.getId());
            deviceLicense.setActivationDate(new Date());
            deviceLicenseRepository.save(deviceLicense);
            logger.info("Лицензия {} активирована на устройстве с ID {}", activationRequest.getCode(), device.getId());


            license.setDeviceCount(license.getDeviceCount() - 1);
            licenseRepository.save(license);
            logger.info("Количество доступных мест для активации на лицензии с кодом {} уменьшено на 1", activationRequest.getCode());


            String description = "Лицензия активирована на устройстве " + device.getName();
            Date changeDate = new Date();
            licenseHistoryService.recordLicenseChange(license.getId(), license.getUser().getId(), "Активирована", changeDate, description);
            logger.info("Запись изменений лицензии в историю завершена");
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Успешная активация лицензии с ID: " + license.getId(), "Лицензия");



            Ticket ticket = Ticket.createTicket(null, false, license.getEndingDate());
            logger.info("Тикет с подтверждением активации лицензии: {}", ticket);

            return ResponseEntity.status(HttpStatus.OK).body("Лицензия успешно активирована на устройстве");

        } catch (Exception e) {
            logger.error("Произошла ошибка при активации: {}", e.getMessage(), e);
            Ticket ticket = Ticket.createTicket(null, true, null);
            logger.info("Тикет с ошибкой: {}", ticket);
            logLicensing(jwtTokenProvider.getEmailFromRequest(request), user.getUsername(), "Ошибка активации: " + e.getMessage(), "Лицензия");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при активации");
        }
    }

    private void logLicensing(String email, String username, String description, String action) {
        ActionAuthRegHistory historyEntry = new ActionAuthRegHistory();
        historyEntry.setEmail(email);
        historyEntry.setUsername(username);
        historyEntry.setTimeAction(LocalDateTime.now());
        historyEntry.setDescription(description);
        historyEntry.setActionType(action);
        actionAuthRegHistoryRepository.save(historyEntry);
    }

    private String generateActivationCode() {
        final int codeLength = 32;
        final String allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        SecureRandom random = new SecureRandom();
        return random.ints(allowedChars.length(), 0, allowedChars.length())
                .limit(codeLength)
                .mapToObj(i -> String.valueOf(allowedChars.charAt(i)))
                .collect(Collectors.joining());
    }
    public boolean isMacAddressExists(String macAddress) {
        return deviceRepository.existsByMacAddress(macAddress);
    }
    public boolean isDeviceExists(String macAddress, String name) {
        Optional<Device> device = deviceRepository.findByMacAddressAndName(macAddress, name);
        return device.isPresent();
    }
}
