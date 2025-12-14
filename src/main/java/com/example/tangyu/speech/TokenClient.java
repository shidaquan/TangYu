package com.example.tangyu.speech;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.example.tangyu.config.CredentialConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retrieves and caches short-lived access tokens for NLS requests.
 */
public class TokenClient {
    private static final Logger LOG = LoggerFactory.getLogger(TokenClient.class);
    private static final String REGION = "cn-shanghai";
    private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    private static final String API_VERSION = "2019-02-28";

    private final CredentialConfig credentialConfig;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public TokenClient(CredentialConfig credentialConfig) {
        this.credentialConfig = credentialConfig;
    }

    public String getToken() {
        CachedToken existing = cachedToken.get();
        if (existing != null && existing.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return existing.token;
        }

        String token = requestNewToken();
        // Default TTL is 30 minutes, but we refresh earlier via the buffer above.
        cachedToken.set(new CachedToken(token, Instant.now().plusSeconds(30 * 60)));
        return token;
    }

    private String requestNewToken() {
        try {
            DefaultProfile profile = DefaultProfile.getProfile(REGION, credentialConfig.getAccessKeyId(), credentialConfig.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(API_VERSION);
            request.setSysAction("CreateToken");

            LOG.info("Requesting new NLS token from {}", DOMAIN);
            CommonResponse response = client.getCommonResponse(request);
            TokenResponse parsed = TokenResponse.parse(response.getData());
            LOG.info("Token created, expires at {}", parsed.getExpireTime());
            return parsed.getId();
        } catch (ClientException e) {
            throw new RuntimeException("Failed to create token", e);
        }
    }

    private record CachedToken(String token, Instant expiresAt) { }
}
