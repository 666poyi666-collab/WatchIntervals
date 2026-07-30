package com.poyi.watchintervals.phone;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneCloudSetupSpecTest {
    @Test public void activeSetupAdvertisesCloudV3InsteadOfRetiredEncryptionFlow() {
        assertTrue(PhoneCloudSetupSpec.ENDPOINT_HINT.endsWith("/sync/v3/exchange"));
        assertFalse(PhoneCloudSetupSpec.ENDPOINT_HINT.contains("/sync/v2/"));
        assertFalse(PhoneCloudSetupSpec.TITLE.contains("加密"));
        assertFalse(PhoneCloudSetupSpec.SAVE_ACTION.contains("加密"));
        assertTrue(PhoneCloudSetupSpec.SECURITY_NOTE.contains("Android Keystore"));
    }
}
