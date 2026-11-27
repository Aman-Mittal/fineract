@InputValidationFeature
Feature: Input validation

  Scenario Outline: List endpoints accept a column name as orderBy
    Then Listing "<list>" ordered by "id" with sort order "DESC" is accepted
    Then Listing "<list>" ordered by "id" with sort order "asc" is accepted

    Examples:
      | list                      |
      | journal entries           |
      | scheduler job run history |
      | loans                     |

  Scenario Outline: List endpoints reject an orderBy that is not a column name
    Then Listing "<list>" ordered by "(select 1)" with sort order "ASC" is rejected
    Then Listing "<list>" ordered by "id; select 1" with sort order "ASC" is rejected

    Examples:
      | list                      |
      | journal entries           |
      | scheduler job run history |
      | loans                     |

  Scenario Outline: List endpoints reject a sort order other than ASC or DESC
    Then Listing "<list>" ordered by "id" with sort order "id" is rejected
    Then Listing "<list>" ordered by "id" with sort order "ASC, id" is rejected

    Examples:
      | list                      |
      | journal entries           |
      | scheduler job run history |
      | loans                     |

  Scenario: A client charge is only found through the client it belongs to
    When Admin sets the business date to "1 January 2025"
    When Admin creates two clients and adds a charge to the second client
    Then Waiving the client charge through the first client is not found
    Then Paying the client charge through the first client is not found
    Then Paying the client charge through the second client succeeds

  Scenario: A user can only grant permissions that the user holds
    When Admin creates a role named "INPUT_VALIDATION_TARGET_ROLE"
    When Admin creates new user with "INPUT_VALIDATION_ROLE_EDITOR" username, "INPUT_VALIDATION_ROLE_EDITOR_ROLE" role name and given permissions:
      | READ_ROLE        |
      | UPDATE_ROLE      |
      | PERMISSIONS_ROLE |
    Then Created user can grant permission "READ_ROLE" to the last created role
    Then Created user cannot grant permission "ALL_FUNCTIONS" to the last created role
    Then Created user cannot grant permission "CREATE_LOAN" to the last created role

  Scenario: The super user role can only be modified by a user holding all functions
    When Admin creates new user with "INPUT_VALIDATION_SUPER_ROLE" username, "INPUT_VALIDATION_SUPER_ROLE_ROLE" role name and given permissions:
      | READ_ROLE        |
      | UPDATE_ROLE      |
      | PERMISSIONS_ROLE |
    Then Created user cannot modify the permissions of the super user role
    Then Admin can modify the permissions of the super user role

  Scenario: A template with a plain placeholder is merged
    When Admin creates a template with text "Hello {{name}}"
    Then Merging the last created template with value "World" succeeds

  Scenario: A template that includes another template by name is rejected
    When Admin creates a template with text "Hello {{>other}}"
    Then Merging the last created template with value "World" is rejected

  Scenario: An ad hoc query is updated only with a valid SQL statement
    When Admin creates an ad hoc query
    Then Updating the last created ad hoc query with SQL "SELECT id FROM m_client WHERE 1 = 0" succeeds
    Then Updating the last created ad hoc query with SQL "SELECT id FROM m_client; drop table x" is rejected

  @ExternalServicePropertiesRestore
  Scenario: Secret external service properties are returned masked
    When Admin sets the S3 external service properties s3_access_key to "ClearAccessKey123" and s3_secret_key to "ClearSecretKey456"
    Then The S3 external service properties are returned without the values "ClearAccessKey123" and "ClearSecretKey456"

  Scenario Outline: A hook with an internal payload address is rejected
    Then Creating a hook with payload URL "<url>" is rejected with a validation error

    Examples:
      | url                       |
      | http://127.0.0.1:9/       |
      | http://169.254.169.254/   |
