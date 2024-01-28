# OWASP Top 10 - A01 2021 - Broken Access Control

In this example project we will present use cases of Broken Access Control category from OWASP Top 10.
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

Insecure direct object references occurs when you expose internal identifier of the object to the outside world.

Usually it is about exposing primary-key of some DB table's entity.

⛔️ **Wrong implementation** can be found in `/account-unsafe/{id}` endpoint.

In the implementation we have `Database` with entities referenced with integer primary keys.
When this is spotted by the Hacker, first thing that he/she can try is to iterate though all possible integer numbers to obtain sensitive data.

In the test `cz.bedla.owasptop10.InsecureDirectObjectReferencesControllerTests.referenceObjectWithSequenceIds` you can see how hacker is poking our unsecured endpoint to get the private data.

⚠️ **Naive fix implementation** can be to use some random values as object identifiers.

This can be found at `/account-uuid/{uuid}` endpoint. You can se here that we use `UUID` class as source of randomness.

In some cases this can be good enough, because this kind of identifier are hard to guess (compared to integer primary keys).

There is one catch ⛔! In case of whole primary key data leakage (does not matter if intended or unintended), untrusted party can still download all the data using list of valid UUIDs.

In the test `cz.bedla.owasptop10.InsecureDirectObjectReferencesControllerTests.referenceObjectWithUuids` you can see this data leak example.
See usage of `cz.bedla.owasptop10.Database.leakAccountUuids` method in the test.

✅ **Correct implementation** is to use authorization to data belonging to particular user.

Basically fix is about using user identity (obtained during Authentication) to match of allowed users that can access entity (authorized user).

Take a look into `cz.bedla.owasptop10.InsecureDirectObjectReferencesController.checkAuthorization` method where we check authenticated user identity against configured authorization map in `Database`.

We use [Keycloak](https://www.keycloak.org/) (Identity and Access Management server) running using [Testcontainers](https://testcontainers.com/) (Orchestration of [Docker](https://www.docker.com/) containers
inside [JUnit](https://junit.org/junit5/) tests) to have real world runnable example.

In Keycloak we create three users `my-client-rimmer` and `my-client-kryton` and `my-client-lister`. And assign them entities.

| User               | Entity                            |
|--------------------|-----------------------------------|
| `my-client-rimmer` | UserAccount(3, "Mia Wallace")     |
| `my-client-kryton` | UserAccount(1, "Vincent Vega")    |
| `my-client-kryton` | UserAccount(2, "Jules Winnfield") |
| `my-client-lister` | no-entity assigned                |

From the test `cz.bedla.owasptop10.InsecureDirectObjectReferencesControllerTests.referenceObjectWithAuthorization` you can see that when we access entity with wrong user, we receive `403 Forbidden` HTTP Status code as response.

Implementation shows that we can referer entities using integer primary-keys (⚠️ less secure when Elevation of privilege occurs) or using UUIDs (✅ preferred variant).
