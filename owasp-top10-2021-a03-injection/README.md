# OWASP Top 10 - A03 2021 - Injection

In this example project we will present two use cases of Injection category from OWASP Top 10.
For more details you can take a look into OWASP web page here [https://owasp.org/Top10/A03_2021-Injection/](https://owasp.org/Top10/A03_2021-Injection/).

## SQL Injection

SQL Injection is one of the most famous Injection attacks in the wild. It is based on interpretation of string containing SQL expression and sent to SQL server.

Usually we do not use simple SQL statements, but we enrich them with parameters.
This kind of usage is crucial part of SQL injection to occur.

When constructing SQL statement string we have to options:

- prepare SQL string by ourselves with all parameters
    - for example: `SELECT * FROM emplyee WHERE name LIKE '%smid%'`
- use SQL statement parameters and pass values with types specified
    - for example: `SELECT * FROM emplyee WHERE name LIKE ?name-param?`
        - `name-param` is `'%smid%'` as `VARCHAR` data-type
    - How we pass parameters to SQL server is defined by JDBC Driver specification.
    - We use prepared-statements for passing the parameters - see below for details

When we create SQL statement parameters by ourselves outside attacker has very easy job.
Let's assume that we have Endpoint parameter that can be used to sent value filled by UI user.
Attacker can craft value to bypass our value and fill in own values, because we have security issue in our code like this:

```kotlin
fun getValue(name: String): Result {
    val result = db.query("SELECT * FROM employee WHERE name LIKE '${name}'")
    return result
}
```

Because we are using basic `String` values concatenation, attacker can send parameter `name = "' OR 1=1 --"` and bypass possible security check we can have in later parts of SQL.

Value `' OR 1=1 --` will be interpreted as:

1. With `'` finish string value
2. `OR 1=1` we do not care about previous conditions, this is always `true`
3. `--` make rest of the string value commented out
4. And result will be all data from table `employee` returned to the caller

**Endpoint `/user-injection` with SQL injection ⛔️**

This endpoint contains SQL injection security issue.
You can find it in the `cz.bedla.owasptop10.UserRepository.findUserInjection` method where we execute SQL statement with parameter taken from Endpoint call request.

```kotlin
return jdbcTemplate.query("SELECT * FROM public.user WHERE id = '$id'")
```

In the test `cz.bedla.owasptop10.SqlInjectionControllerTest.unsafeFindUsersSingleUser` method you can see harmless call of this endpoint.
We ask for the user by ID and the User is returned if found.

If we pass wrong identifier no User is returned in the `cz.bedla.owasptop10.SqlInjectionControllerTest.unsafeFindUsersNoMatch` test.

When attacker calls this endpoint in `cz.bedla.owasptop10.SqlInjectionControllerTest.unsafeFindUsersWithSqlInjection` test with value like `' OR 1=1 --` our endpoint will return all data from `user` table and leak all the data.
Why? You can find it above described.

**Endpoint `/user` with SQL injection fixed ✅**

When we call `/user` endpoint where we fixed SQL injection by using `PreparedStatement` under the hood, everything behaves as expected.

| Endpoint                    | Result                                        |
|-----------------------------|-----------------------------------------------|
| `/user?id=a1`               | `[User("Vincent Vega")]` array with 1 element |
| `/user?id=random-value-999` | `[]` empty array                              |
| `/user?id=' OR 1=1 --`      | `[]` empty array ✅                            |

Fix is about changing SQL statement to `SELECT * FROM public.user WHERE id = ?`, where `?` is position of SQL parameter.
JDBC driver now knows that we are passing string parameter and correctly sends data to SQL server where SQL server creates Execution plan with this parameter used (see below for details).

**Naive fix of SQL injection ⚠️**

Sometimes we might be tempted to do apply our own escaping/validation of the values sent to us from outside world.
This might work for numeric data-types (or similar) because set of possible characters is finite, and we can easily do validation of the input or escape special characters.

This approach does not scale and is insecure.
- We might miss some characters to escape of validate
- We are forcing SQL server not to cache prepared statements
  - This will introduce performance problems (see below for details)

**Usage of Prepared statement cache**

SQL server uses internal engine to interpret SQL statements and return results to the caller.
You can imagine this as compilation of string and executing the result on DB server side.

When SQL server compiles SQL statement it creates execution plan that is later executed.

To make it fast when we call same SQL statements again and again, DB server uses so-called **prepared statement cache**.
When it sees same SQL statement and have prepared statement in cache with associated execution plan, it is reused and not compiled again.
This is performance boost.

When you take a look into `cz.bedla.owasptop10.SqlInjectionControllerTest.preparedStatements` test where we call
`/prepare-statement-dump-injection` and `/prepare-statement-dump` endpoints, 
you can see internal statistics about how SQL server is using prepared statement cache.

1. When we call `/prepare-statement-dump-injection` where we do string concatenation result is following
```
1.	S_24 -> SELECT * FROM public.user WHERE id = 'xxx1-2024-01-28T19:06:34.394602300'
2.	S_25 -> SELECT * FROM public.user WHERE id = 'xxx2-2024-01-28T19:06:34.398236200'
3.	S_26 -> SELECT * FROM public.user WHERE id = 'xxx3-2024-01-28T19:06:34.400360900'
4.	S_27 -> SELECT * FROM public.user WHERE id = 'xxx4-2024-01-28T19:06:34.401960400'
5.	S_28 -> SELECT * FROM public.user WHERE id = 'xxx5-2024-01-28T19:06:34.403552300'
6.	S_29 -> SELECT * FROM public.user WHERE id = 'xxx6-2024-01-28T19:06:34.405148800'
7.	S_30 -> SELECT * FROM public.user WHERE id = 'xxx7-2024-01-28T19:06:34.406777500'
8.	S_31 -> SELECT * FROM public.user WHERE id = 'xxx8-2024-01-28T19:06:34.408395200'
9.	S_32 -> SELECT * FROM public.user WHERE id = 'xxx9-2024-01-28T19:06:34.411234600'
10.	S_33 -> SELECT * FROM public.user WHERE id = 'xxx10-2024-01-28T19:06:34.413545700'
```

You can see that SQL server has prepared 10 prepared statements with its own execution plans.
You can also spot that only difference is in the `id` column value. **This is waste of resources ⛔️.**

2. When we call `/prepare-statement-dump` where we use prepared statement parameters result is following
```
1.	S_40 -> SELECT * FROM public.user WHERE id = $1
```

Only one prepared statement and execution is created and used to return values of 10 executions.
**Now SQL server is happy with usage of resources ✅.**

## Remote code execution

...
