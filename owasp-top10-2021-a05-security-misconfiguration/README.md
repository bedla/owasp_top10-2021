# OWASP Top 10 - A05 2021 - Security misconfiguration

In this example project we will present use cases of Security Misconfiguration category from OWASP Top 10.
For more details you can take a look into OWASP web page here [https://owasp.org/Top10/A05_2021-Security_Misconfiguration/](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/).

From description there might be many types of Security misconfiguration.

We will focus on Misconfiguration of our framework (for example Spring Boot),
where we have option to return detail information about system using exception handlers.

All use-cases are described in `cz.bedla.owasptop10.SecurityMisconfigurationTest.TestCase` enum class.

We have to basic parts of this problem:

1. Configuration of framework's error handlers

```yaml
server:
  error:
    include-binding-errors: always
    include-exception: true
    include-message: always
    include-stacktrace: always
```

Attacker should not get information about internal behavior of the system.
This might help him/her to assume structure of the system and vulnerabilities hidden inside.
All 4 options above are in danger zone and should not be enabled.
That's why they are not enabled by default.

2. Using custom error handlers

When using custom error handler we might expose too much information to attacker.
Or we can leak some data to him/her.

**Best practice** is to return error identifier (for example UUID) to the caller.
Log detailed error message and later this identifier can be used to find exact error in the logs.

**Test cases:**

1. Test case `GLOBAL_NOT_FOUND` ✅

This test cases shows that when we have default configuration (not to expose internal error to the caller)
and `404 Not Found` HTTP Status code is returned, we do not expose anything interesting to the hacker.

2. Test case `GLOBAL_OK` ✅

This test cases shows standard `200 OK` HTTP Status code behavior.

3. Test case `GLOBAL_UNHANDLED_EXCEPTION_INSECURE` ⛔️

In case of exception thrown and having insecure global exception handler configuration enabled,
we can expect internal information leakage.

- Whole stack-trace of exception is returned
- Exception contains erroneous entity containing internal integer primary key

4. Test case `GLOBAL_UNHANDLED_EXCEPTION_SECURE` ✅

In this case we disabled all properties to expose internals of our system.
And as we can see from test execution we can expect that not data is leaked.

5. Test cases `CUSTOM_HANDLER_RAW_ENTITY` and `PROBLEM_DETAILS_HANDLER_RAW_ENTITY` ⛔️

Those two test cases similarly shows that when we send raw-entity with our custom handler,
we have no control if this entity does not contain some of the internal data.

In this example it returns internal integer primary key of the entity.
This might be abused with "Insecure direct object references" attack.

6. Test cases `CUSTOM_HANDLER_ENTITY_ID` and `PROBLEM_DETAILS_HANDLER_ENTITY_ID` ⛔️

Those two test cases similarly shows that when we send entity identifier with our custom handler,
we are exposing internal integer primary key of the entity.

This might be abused with "Insecure direct object references" attack.

7. Test cases `CUSTOM_HANDLER_ENTITY_UUID` and `PROBLEM_DETAILS_HANDLER_ENTITY_UUID` ✅

To fix problem with internal integer primary key of the entity exposure,
we might return hard to guess UUID of the entity.

Again we might be vulnerable to "Broken access control" attack, so consultation of this attack is recommended.

**Note:** Difference between `CUSTOM_HANDLER_*` and `PROBLEM_DETAILS_HANDLER_*` test-cases is that 
with first we construct our own error JSON object.
And with later one we re-use of [Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) facility of Spring framework.
