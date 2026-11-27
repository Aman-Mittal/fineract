/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.test.stepdef.common;

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.assertj.core.api.Assertions.assertThat;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.AdHocRequest;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.ExternalServicesPropertiesData;
import org.apache.fineract.client.models.HookCreateRequest;
import org.apache.fineract.client.models.HookEventData;
import org.apache.fineract.client.models.PostClientsClientIdChargesChargeIdRequest;
import org.apache.fineract.client.models.PostClientsClientIdChargesRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.models.TemplateCreateRequest;
import org.apache.fineract.test.api.FineractClientConfiguration;
import org.apache.fineract.test.data.ChargeProductResolver;
import org.apache.fineract.test.data.ChargeProductType;
import org.apache.fineract.test.data.job.DefaultJob;
import org.apache.fineract.test.data.job.JobResolver;
import org.apache.fineract.test.factory.ClientRequestFactory;
import org.apache.fineract.test.helper.Utils;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;

@RequiredArgsConstructor
public class InputValidationStepDef extends AbstractStepDef {

    private static final String FIRST_CLIENT_ID = "inputValidationFirstClientId";
    private static final String SECOND_CLIENT_ID = "inputValidationSecondClientId";
    private static final String CLIENT_CHARGE_ID = "inputValidationClientChargeId";
    private static final String ROLE_ID = "inputValidationRoleId";
    private static final String AD_HOC_QUERY_ID = "inputValidationAdHocQueryId";
    private static final String TEMPLATE_ID = "inputValidationTemplateId";

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String LOCALE = "en";
    private static final String CHARGE_DATE = "01 January 2025";
    private static final String PAY_COMMAND = "paycharge";
    private static final String WAIVE_COMMAND = "waive";
    private static final long SUPER_USER_ROLE_ID = 1L;
    private static final String VALID_AD_HOC_SQL = "SELECT id FROM m_client WHERE 1 = 0";

    /**
     * Calls whose generated client method is not referenced by other step classes; sent through a minimal interface.
     */
    @Headers({ "Accept: application/json", "Content-Type: application/json" })
    public interface RawCallsApi {

        @RequestLine("PUT /v1/adhocquery/{adHocId}")
        CommandProcessingResult updateAdHocQuery(@Param("adHocId") Long adHocId, AdHocRequest request);

        @RequestLine("POST /v1/templates/{templateId}")
        @Headers({ "Accept: text/html", "Content-Type: application/json" })
        Response mergeTemplate(@Param("templateId") Long templateId, Map<String, Object> values);
    }

    private final FineractFeignClient fineractClient;
    private final FineractClientConfiguration fineractClientConfiguration;
    private final ClientRequestFactory clientRequestFactory;
    private final ChargeProductResolver chargeProductResolver;
    private final JobResolver jobResolver;

    // ---- orderBy / sortOrder -------------------------------------------------------------------------------------

    @Then("Listing {string} ordered by {string} with sort order {string} is accepted")
    public void listingIsAccepted(final String list, final String orderBy, final String sortOrder) {
        ok(() -> list(list, blankToNull(orderBy), blankToNull(sortOrder)));
    }

    @Then("Listing {string} ordered by {string} with sort order {string} is rejected")
    public void listingIsRejected(final String list, final String orderBy, final String sortOrder) {
        final CallFailedRuntimeException exception = fail(() -> list(list, blankToNull(orderBy), blankToNull(sortOrder)));
        assertClientError(exception);
    }

    private Object list(final String list, final String orderBy, final String sortOrder) {
        switch (list) {
            case "journal entries": {
                final Map<String, Object> params = new HashMap<>();
                if (orderBy != null) {
                    params.put("orderBy", orderBy);
                }
                if (sortOrder != null) {
                    params.put("sortOrder", sortOrder);
                }
                return fineractClient.journalEntries().retrieveAllJournalEntries(params);
            }
            case "scheduler job run history": {
                final Long jobId = jobResolver.resolve(DefaultJob.ADD_ACCRUAL_TRANSACTIONS);
                return fineractClient.schedulerJob().retrieveHistory(jobId, 0, 10, orderBy, sortOrder);
            }
            case "loans":
                return fineractClient.loans().retrieveAllLoans(null, 0, 10, orderBy, sortOrder, null, null, null, null);
            default:
                throw new IllegalArgumentException("Unknown list: " + list);
        }
    }

    // ---- client charge belongs to the client ---------------------------------------------------------------------

    @When("Admin creates two clients and adds a charge to the second client")
    public void createTwoClientsAndChargeSecond() {
        final PostClientsResponse first = ok(
                () -> fineractClient.clients().createClient(clientRequestFactory.defaultClientCreationRequest()));
        final PostClientsResponse second = ok(
                () -> fineractClient.clients().createClient(clientRequestFactory.defaultClientCreationRequest()));
        final Long chargeId = chargeProductResolver.resolve(ChargeProductType.CLIENT_TEST_CHARGE_FEE);
        final PostClientsClientIdChargesRequest request = new PostClientsClientIdChargesRequest() //
                .chargeId(chargeId) //
                .amount(BigDecimal.TEN) //
                .dueDate(CHARGE_DATE) //
                .dateFormat(DATE_FORMAT) //
                .locale(LOCALE);
        final Long clientChargeId = ok(() -> fineractClient.clientCharges().createClientCharge(second.getClientId(), request))
                .getResourceId();
        testContext().set(FIRST_CLIENT_ID, first.getClientId());
        testContext().set(SECOND_CLIENT_ID, second.getClientId());
        testContext().set(CLIENT_CHARGE_ID, clientChargeId);
    }

    @Then("Paying the client charge through the first client is not found")
    public void payThroughFirstClientNotFound() {
        assertNotFound(() -> payOrWaive(FIRST_CLIENT_ID, PAY_COMMAND));
    }

    @Then("Waiving the client charge through the first client is not found")
    public void waiveThroughFirstClientNotFound() {
        assertNotFound(() -> payOrWaive(FIRST_CLIENT_ID, WAIVE_COMMAND));
    }

    @Then("Paying the client charge through the second client succeeds")
    public void payThroughSecondClientSucceeds() {
        ok(() -> payOrWaive(SECOND_CLIENT_ID, PAY_COMMAND));
    }

    private Object payOrWaive(final String clientKey, final String command) {
        final Long clientId = testContext().get(clientKey);
        final Long clientChargeId = testContext().get(CLIENT_CHARGE_ID);
        final PostClientsClientIdChargesChargeIdRequest request = new PostClientsClientIdChargesChargeIdRequest() //
                .transactionDate(CHARGE_DATE) //
                .amount(10) //
                .dateFormat(DATE_FORMAT) //
                .locale(LOCALE);
        return fineractClient.clientCharges().payOrWaiveClientCharge(clientId, clientChargeId, request, command);
    }

    // ---- role permissions ----------------------------------------------------------------------------------------

    @When("Admin creates a role named {string}")
    public void createRole(final String name) {
        final PostRolesRequest request = new PostRolesRequest().name(Utils.randomStringGenerator(name, 8)).description(name);
        final Long roleId = ok(() -> fineractClient.roles().createRole(request)).getResourceId();
        testContext().set(ROLE_ID, roleId);
    }

    @Then("Created user cannot grant permission {string} to the last created role")
    public void createdUserCannotGrant(final String permission) {
        final Long roleId = testContext().get(ROLE_ID);
        final CallFailedRuntimeException exception = fail(
                () -> createdUserClient().roles().updateRolePermissions(roleId, permissionRequest(permission)));
        assertThat(exception.getStatus()).isEqualTo(403);
    }

    @Then("Created user can grant permission {string} to the last created role")
    public void createdUserCanGrant(final String permission) {
        final Long roleId = testContext().get(ROLE_ID);
        ok(() -> createdUserClient().roles().updateRolePermissions(roleId, permissionRequest(permission)));
    }

    @Then("Created user cannot modify the permissions of the super user role")
    public void createdUserCannotModifySuperUserRole() {
        final CallFailedRuntimeException exception = fail(
                () -> createdUserClient().roles().updateRolePermissions(SUPER_USER_ROLE_ID, permissionRequest("READ_ROLE")));
        assertThat(exception.getStatus()).isEqualTo(403);
    }

    @Then("Admin can modify the permissions of the super user role")
    public void adminCanModifySuperUserRole() {
        ok(() -> fineractClient.roles().updateRolePermissions(SUPER_USER_ROLE_ID, permissionRequest("READ_ROLE")));
    }

    private static PutRolesRoleIdPermissionsRequest permissionRequest(final String permission) {
        return new PutRolesRoleIdPermissionsRequest().permissions(Map.of(permission, true));
    }

    // ---- templates -----------------------------------------------------------------------------------------------

    @When("Admin creates a template with text {string}")
    public void createTemplate(final String text) {
        final TemplateCreateRequest request = new TemplateCreateRequest().name(Utils.randomStringGenerator("TEMPLATE_", 8)).type(0)
                .entity(0).text(text);
        final Long templateId = ok(() -> fineractClient.templates().createTemplate(request)).getResourceId();
        testContext().set(TEMPLATE_ID, templateId);
    }

    @Then("Merging the last created template with value {string} succeeds")
    public void mergeSucceeds(final String value) {
        final Long templateId = testContext().get(TEMPLATE_ID);
        final Response response = ok(() -> rawCalls().mergeTemplate(templateId, Map.of("name", value)));
        response.close();
    }

    @Then("Merging the last created template with value {string} is rejected")
    public void mergeIsRejected(final String value) {
        final Long templateId = testContext().get(TEMPLATE_ID);
        final CallFailedRuntimeException exception = fail(() -> rawCalls().mergeTemplate(templateId, Map.of("name", value)));
        assertThat(exception.getStatus()).isGreaterThanOrEqualTo(400);
    }

    // ---- ad hoc queries ------------------------------------------------------------------------------------------

    @When("Admin creates an ad hoc query")
    public void createAdHocQuery() {
        final AdHocRequest request = adHocRequest(VALID_AD_HOC_SQL);
        final Long id = ok(() -> fineractClient.adhocQuery().createAdHocQuery(request)).getResourceId();
        testContext().set(AD_HOC_QUERY_ID, id);
    }

    @Then("Updating the last created ad hoc query with SQL {string} is rejected")
    public void adHocUpdateIsRejected(final String sql) {
        final Long id = testContext().get(AD_HOC_QUERY_ID);
        final CallFailedRuntimeException exception = fail(() -> rawCalls().updateAdHocQuery(id, adHocRequest(sql)));
        assertClientError(exception);
    }

    @Then("Updating the last created ad hoc query with SQL {string} succeeds")
    public void adHocUpdateSucceeds(final String sql) {
        final Long id = testContext().get(AD_HOC_QUERY_ID);
        ok(() -> rawCalls().updateAdHocQuery(id, adHocRequest(sql)));
    }

    private static AdHocRequest adHocRequest(final String sql) {
        return new AdHocRequest().name(Utils.randomStringGenerator("ADHOC_", 8)).query(sql).tableName("m_client").tableFields("id")
                .isActive(false);
    }

    // ---- external service properties -----------------------------------------------------------------------------

    @When("Admin sets the S3 external service properties s3_access_key to {string} and s3_secret_key to {string}")
    public void setS3Properties(final String accessKey, final String secretKey) {
        // The generated request model only covers the SMTP properties, so the properties are sent as a plain map.
        final ExternalServiceUpdateApi api = fineractClient.create(ExternalServiceUpdateApi.class);
        ok(() -> api.updateProperties("S3", Map.of("s3_access_key", accessKey, "s3_secret_key", secretKey)));
    }

    @Then("The S3 external service properties are returned without the values {string} and {string}")
    public void s3PropertiesAreMasked(final String accessKey, final String secretKey) {
        final List<ExternalServicesPropertiesData> properties = ok(
                () -> fineractClient.externalServices().retrieveExternalServicesConfiguration("S3"));
        assertThat(properties).extracting(ExternalServicesPropertiesData::getName).contains("s3_access_key", "s3_secret_key");
        assertThat(properties).filteredOn(p -> "s3_access_key".equals(p.getName())).extracting(ExternalServicesPropertiesData::getValue)
                .doesNotContain(accessKey);
        assertThat(properties).filteredOn(p -> "s3_secret_key".equals(p.getName())).extracting(ExternalServicesPropertiesData::getValue)
                .doesNotContain(secretKey);
    }

    @Headers({ "Accept: application/json", "Content-Type: application/json" })
    public interface ExternalServiceUpdateApi {

        @RequestLine("PUT /v1/externalservice/{serviceName}")
        CommandProcessingResult updateProperties(@Param("serviceName") String serviceName, Map<String, String> properties);
    }

    // ---- hooks ---------------------------------------------------------------------------------------------------

    @Then("Creating a hook with payload URL {string} is rejected with a validation error")
    public void hookCreationIsRejected(final String payloadUrl) {
        final HookCreateRequest request = new HookCreateRequest() //
                .name("Web") //
                .displayName(Utils.randomStringGenerator("Hook_DisplayName_", 5)) //
                .isActive(true) //
                .config(Map.of("Content Type", "json", "Payload URL", payloadUrl)) //
                .events(List.of(new HookEventData().actionName("CREATE").entityName("OFFICE")));
        final CallFailedRuntimeException exception = fail(() -> fineractClient.hooks().createHook(request));
        assertThat(exception.getStatus()).isEqualTo(400);
        assertThat(exception.getResponseBody()).contains("url.invalid");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private FineractFeignClient createdUserClient() {
        final String username = testContext().get(TestContextKey.CREATED_SIMPLE_USER_USERNAME);
        final String password = testContext().get(TestContextKey.CREATED_SIMPLE_USER_PASSWORD);
        return fineractClientConfiguration.fineractFeignClientForUser(username, password);
    }

    private RawCallsApi rawCalls() {
        return fineractClient.create(RawCallsApi.class);
    }

    private static void assertNotFound(final Supplier<?> call) {
        assertThat(fail(call).getStatus()).isEqualTo(404);
    }

    private static void assertClientError(final CallFailedRuntimeException exception) {
        assertThat(exception.getStatus()).isBetween(400, 499);
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
