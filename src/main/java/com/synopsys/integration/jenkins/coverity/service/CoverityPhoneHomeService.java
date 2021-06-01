package com.synopsys.integration.jenkins.coverity.service;

import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.impl.client.HttpClientBuilder;

import com.google.gson.Gson;
import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.LicenseDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.VersionDataObj;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.phonehome.PhoneHomeClient;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.phonehome.PhoneHomeService;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBodyBuilder;

public class CoverityPhoneHomeService {
    private final IntLogger logger;
    private final JenkinsVersionHelper jenkinsVersionHelper;
    private final WebServiceFactory webServiceFactory;

    public CoverityPhoneHomeService(IntLogger logger, JenkinsVersionHelper jenkinsVersionHelper, WebServiceFactory webServiceFactory) {
        this.logger = logger;
        this.jenkinsVersionHelper = jenkinsVersionHelper;
        this.webServiceFactory = webServiceFactory;
    }

    public Optional<PhoneHomeResponse> phoneHome(String baseUrl) {
        try {
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            Gson gson = new Gson();
            PhoneHomeClient phoneHomeClient = new PhoneHomeClient(logger, httpClientBuilder, gson);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            PhoneHomeService phoneHomeService = PhoneHomeService.createAsynchronousPhoneHomeService(logger, phoneHomeClient, executor);

            PhoneHomeRequestBodyBuilder phoneHomeRequestBodyBuilder = this.createPhoneHomeBuilder(baseUrl);

            jenkinsVersionHelper.getJenkinsVersion()
                .ifPresent(jenkinsVersionString -> phoneHomeRequestBodyBuilder.addToMetaData("jenkins.version", jenkinsVersionString));
            PhoneHomeRequestBody phoneHomeRequestBody = phoneHomeRequestBodyBuilder.build();

            return Optional.ofNullable(phoneHomeService.phoneHome(phoneHomeRequestBody));
        } catch (Exception e) {
            logger.trace("Phone home failed due to an unexpected exception:", e);
        }

        return Optional.empty();
    }

    public PhoneHomeRequestBodyBuilder createPhoneHomeBuilder(String baseUrl) {
        String customerName;
        String cimVersion;

        try {
            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            try {
                LicenseDataObj licenseDataObj = configurationService.getLicenseConfiguration();
                customerName = licenseDataObj.getCustomer();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
                customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }

            try {
                VersionDataObj versionDataObj = configurationService.getVersion();
                cimVersion = versionDataObj.getExternalVersion();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity version: " + e.getMessage());
                cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }
        } catch (MalformedURLException e) {
            logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
            logger.trace("Couldn't get the Coverity version: " + e.getMessage());
            cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
        }

        String pluginVersion = jenkinsVersionHelper.getPluginVersion("synopsys-coverity").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        return PhoneHomeRequestBodyBuilder.createForCoverity("synopsys-coverity", customerName, baseUrl, pluginVersion, cimVersion);
    }
}
