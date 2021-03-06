/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.auth.oauth.impl;

import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.Scope;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.wso2.carbon.auth.client.registration.dao.ApplicationDAO;
import org.wso2.carbon.auth.client.registration.model.Application;
import org.wso2.carbon.auth.core.api.UserNameMapper;
import org.wso2.carbon.auth.core.exception.ExceptionCodes;
import org.wso2.carbon.auth.oauth.OAuthConstants;
import org.wso2.carbon.auth.oauth.configuration.models.OAuthConfiguration;
import org.wso2.carbon.auth.oauth.dao.OAuthDAO;
import org.wso2.carbon.auth.oauth.dto.AccessTokenContext;
import org.wso2.carbon.auth.oauth.dto.AccessTokenDTO;
import org.wso2.carbon.auth.oauth.exception.OAuthDAOException;
import org.wso2.carbon.auth.oauth.internal.ServiceReferenceHolder;
import org.wso2.carbon.auth.user.mgt.UserStoreManager;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RefreshGrantHandlerTest {

    private RefreshGrantHandler refreshGrantHandler;
    private OAuthDAO oauthDAO;
    private ApplicationDAO applicationDAO;
    private UserNameMapper userNameMapper;
    String clientId = "JgUsk2mQ_WL0ffmpRSpHDJWFjvEa";
    String clientSecret = "KQd8QXgV3bG1nFOGRDf7ib6HJu4a";
    String refreshToken = "sJZMLpTkK6u5FPFtLDPX3GIox19aNFR5jnbye_GzX9k";
    String scope = "scope1";
    String authorization;
    AccessTokenContext context;
    Map<String, String> queryParameters;
    UserStoreManager userStoreManager;

    @Before
    public void init() throws Exception {
        ServiceReferenceHolder.getInstance().setConfig(new OAuthConfiguration());

        oauthDAO = Mockito.mock(OAuthDAO.class);
        applicationDAO = Mockito.mock(ApplicationDAO.class);
        userNameMapper = Mockito.mock(UserNameMapper.class);
        authorization = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        context = new AccessTokenContext();
        queryParameters = new HashMap<>();
        userStoreManager = Mockito.mock(UserStoreManager.class);
        refreshGrantHandler = new RefreshGrantHandler();
        refreshGrantHandler.init(userNameMapper, oauthDAO, userStoreManager, applicationDAO);
    }

    @Test
    public void testProcessWithInValidateGrant() throws Exception {

        authorization = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        Application application = new Application();
        application
                .setGrantTypes(GrantType.PASSWORD + " " + GrantType.REFRESH_TOKEN + " " + GrantType.CLIENT_CREDENTIALS);
        Mockito.when(applicationDAO.getApplication(clientId)).thenReturn(application);
        Mockito.when(oauthDAO.isClientCredentialsValid(clientId, clientSecret)).thenReturn(true);

        // null refresh token
        queryParameters.put(OAuthConstants.REFRESH_TOKEN_QUERY_PARAM, null);
        refreshGrantHandler.validateGrant(authorization, context, queryParameters);
        Assert.assertEquals(context.getErrorObject().getCode(), OAuth2Error.INVALID_REQUEST.getCode());

        //no access token data for a given refresh token and client id
        queryParameters.put(OAuthConstants.REFRESH_TOKEN_QUERY_PARAM, refreshToken);
        context.getParams().put(OAuthConstants.CLIENT_ID, clientId);
        Mockito.when(oauthDAO.getTokenInfo(refreshToken, clientId)).thenReturn(null);
        refreshGrantHandler.validateGrant(authorization, context, queryParameters);
        Assert.assertEquals(context.getErrorObject().getCode(), RefreshGrantHandler.INVALID_GRANT_ERROR_CODE);

        //error when getting token info
        Mockito.when(oauthDAO.getTokenInfo(refreshToken, clientId)).thenThrow(OAuthDAOException.class);
        try {
            refreshGrantHandler.validateGrant(authorization, context, queryParameters);
            Assert.fail("Exception expected");
        } catch (OAuthDAOException e) {
            Assert.assertTrue(e.getCause() instanceof OAuthDAOException);
            Assert.assertEquals(ExceptionCodes.DAO_EXCEPTION, e.getErrorHandler());
        }
    }

    @Test
    public void testProcessWithValidateGrant() throws Exception {

        AccessTokenDTO accessTokenDTO = new AccessTokenDTO();
        authorization = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        Application application = new Application();
        application
                .setGrantTypes(GrantType.PASSWORD + " " + GrantType.REFRESH_TOKEN + " " + GrantType.CLIENT_CREDENTIALS);
        context.getParams().put(OAuthConstants.CLIENT_ID, clientId);

        Mockito.when(applicationDAO.getApplication(clientId)).thenReturn(application);
        Mockito.when(oauthDAO.isClientCredentialsValid(clientId, clientSecret)).thenReturn(true);
        queryParameters.put(OAuthConstants.REFRESH_TOKEN_QUERY_PARAM, refreshToken);
        Mockito.when(oauthDAO.getTokenInfo(refreshToken, clientId)).thenReturn(accessTokenDTO);

        //check expired refresh token
        accessTokenDTO.setRefreshTokenCreatedTime(System.currentTimeMillis() - 3600000);
        accessTokenDTO.setRefreshTokenValidityPeriod(3600);
        refreshGrantHandler.validateGrant(authorization, context, queryParameters);
        Assert.assertEquals(context.getErrorObject().getCode(), RefreshGrantHandler.INVALID_GRANT_ERROR_CODE);

        // null scopes
        context.getParams().put(OAuthConstants.VALIDITY_PERIOD, 3600L);
        accessTokenDTO.setRefreshTokenCreatedTime(System.currentTimeMillis());
        refreshGrantHandler.validateGrant(authorization, context, queryParameters);
        refreshGrantHandler.validateScopes(context);
        refreshGrantHandler.process(authorization, context, queryParameters);
        Assert.assertTrue(context.isSuccessful());
        Assert.assertEquals(OAuthConstants.SCOPE_DEFAULT,
                context.getAccessTokenResponse().getTokens().getAccessToken().getScope().toString());

        // with scopes
        queryParameters.put(OAuthConstants.SCOPE_QUERY_PARAM, scope);
        context.getParams().put(OAuthConstants.FILTERED_SCOPES, new Scope(scope));
        refreshGrantHandler.validateGrant(authorization, context, queryParameters);
        refreshGrantHandler.process(authorization, context, queryParameters);
        Assert.assertTrue(context.isSuccessful());
        Assert.assertEquals(scope, context.getAccessTokenResponse().getTokens().getAccessToken().getScope().toString());
    }
}
