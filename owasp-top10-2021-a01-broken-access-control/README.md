# OWASP Top 10 - A01 2021 - Broken Access Control

In this example project we will present two use cases of Broken Access Control category from OWASP Top 10.
For more details you can take a look into OWASP web page here [https://owasp.org/Top10/A01_2021-Broken_Access_Control/](https://owasp.org/Top10/A01_2021-Broken_Access_Control/).

## Elevation of Privilege

Elevation of privilege is about wrongly applied security on REST endpoints.
Basically it is about having no security applied to endpoints or having lower level of security.

First take a look into `ElevationOfPrivilegeController` class where we have endpoints and then take a look into authorization configuration in `cz.bedla.owasptop10.A01BrokenAccessControlApplication.filterChain` method.

| Endpoint                              | Authn/Authz | Authz roles                              |
|---------------------------------------|:-----------:|------------------------------------------|
| `/elevation-of-privilege/hello`       |    none     |                                          |
| `/elevation-of-privilege/admin`       |     yes     | `SCOPE_my-admin`, `SCOPE_my-super-admin` |
| `/elevation-of-privilege/super-admin` |     yes     | `SCOPE_my-super-admin`                   |
| `/elevation-of-privilege/technical`   |     yes     |                                          | 
| `/elevation-of-privilege/also-admin`  |    none     | We forgot to specify required role       |

- `/hello` endpoint
    - There is no any authorization or authentication required (anonymous endpoint)
    - This behavior is intended
    - You can see details in `cz.bedla.owasptop10.ElevationOfPrivilegeControllerTests.anonymous` test

Other endpoints are intended as endpoints with Authentication and Authorization applied.

We created `cz.bedla.owasptop10.ElevationOfPrivilegeControllerTests.elevationOfPrivilege_Endpoint` test to present all security aspects with current implementation.
We use [Keycloak](https://www.keycloak.org/) (Identity and Access Management server) running using [Testcontainers](https://testcontainers.com/) (Orchestration of [Docker](https://www.docker.com/) containers
inside [JUnit](https://junit.org/junit5/) tests) to have real world runnable example.

**Test setup**

First we create 3 users in Keycloak, that are later used to login and obtain OAuth2 `access_token`.

**Users and endpoints**

- **User `my-client-super-admin`**
    - Most powerful user
    - With role/scope `my-super-admin`
    - ✅ This user can call all endpoints
        - `/super-admin`
        - `/admin`
        - `/also-admin`
        - `/technical` (authenticated)
        - `/hello` (anonymous)
- **User `my-client-admin`**
    - User used to administration tasks
    - With role/scope `my-admin`
    - ✅ This user can call endpoints
        - `/admin`
        - `/also-admin`
        - `/technical` (authenticated)
        - `/hello` (anonymous)
    - And CAN NOT call endpoint
        - ⛔️ `/super-admin` (which is correctly configured)
- **User `my-client-user`**
    - Standard user
    - With role/scope `my-user`
    - ✅ This user can call endpoints
        - `/technical` (authenticated)
        - `/hello` (anonymous)
    - And CAN NOT call endpoint
        - ⛔️ `/super-admin` (which is correctly configured)
        - ⛔️ `/admin` (which is correctly configured)
    - And CAN call endpoint
        - ⚠️ `/also-admin` (⛔️ which is incorrectly configured ⛔️)
        - We forgot to specify Authorization rules on this endpoint

## Insecure direct object references