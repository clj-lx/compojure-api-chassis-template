# {{full-name}}

An application for FIXME.

### Components

* Embedded Jetty
* Compojure-api & swagger 
  * https://github.com/metosin/compojure-api
    * API validation (spec) https://clojure.org/guides/spec
  * Supports Manifold's `deferred`
* component definition (mount) 
  * https://github.com/tolitius/mount
* metrics (local jarfile, waiting for async support) 
  * https://github.com/metrics-clojure/metrics-clojure
* env loading (omniconf) 
  * https://github.com/grammarly/omniconf
{{#pgsql-hook?}}
* db migrations (migratus & migratus-lein)
 * https://github.com/yogthos/migratus#quick-start-leiningen-2x
{{/pgsql-hook?}}  
{{#html-hook?}}
* Selmer for html rendering
 * https://github.com/yogthos/Selmer
{{/html-hook?}}
{{#oauth2-hook?}}
* Friend & buddy for oauth & auth
 * https://github.com/clojusc/friend-oauth2
 * https://github.com/funcool/buddy-auth
{{/oauth2-hook?}}  
* reloaded workflow through `mount` and `dev/user.clj`   


## Usage

### Change the config.edn at the root

Alternatively, pass the `CONF_FILE` env var or `--conf-file=` argument.
Make sure to create all required variables in the `config.edn` file

### Run the application locally, with an nRepl

Note that there is no route mapped at `/`.

```
;; show help
> lein run -- --help


;; set the server port
> SERVER__PORT=3001 lein repl
> lein ring server --server-port 3001
```

### Run via repl

```
(in-ns 'user)

;; to reload
(do (refresh-all) (go))
```

### Create a migration

Due to dependency on `mount`, we run migratus tasks with a lein alias called `migrations` instead of `migratus`:

```
;; create a migration
> lein migrations create some_migration_name
> ls resources/migrations
```

We can also invoke the from the `{{project-ns}}.db` namespace.

### Calling endpoints

Bear in mind that some endpoints are authenticated/authorized.

#### Token auth

```
> curl -i -X GET  --header 'Authorization: Token 1234567890' 'http://localhost:3001/api/spec/plus?x=1&y=2'
{"total":3}

> curl -i -X GET  --header 'Authorization: Token 1234567890' 'http://localhost:3001/api/auth/user'
{"username":"token-user","role":"api"}
```

#### JWT auth

```
> lein jwt-sign '{:user "test" :role :jwt}'
...
Please use the following 'Authorization' header:
Token eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoidGVzdCIsInJvbGUiOiJqd3QifQ.zzwCRmA95EO0vf1oglSaUHBKU9dvjdDXe_pdQ0jubgI


> curl -i -X GET  --header 'Authorization:Token eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoidGVzdCIsInJvbGUiOiJqd3QifQ.zzwCRmA95EO0vf1oglSaUHBKU9dvjdDXe_pdQ0jubgI' 'http://localhost:3001/api/auth/user'
{"user":"test","role":"jwt"}
```

#### Get current user

```
/user
```

#### File upload

```
;; file upload
> curl -XPOST  "http://localhost:3001/api/spec/file" -F file=@project.clj
```

### Testing

Testing is provided by `clojure.test`. There's also `eftest` runner, providing a task `lein eftest`.
Feel free to investigate the following resources:

* https://github.com/metosin/compojure-api/wiki/Testing-api-endpoints
* https://github.com/weavejester/eftest

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```


### Running standalone (preferred method because of config validation)

```
lein uberjar
lein run --required-option qux --option-from-set bar
REQUIRED_OPTION=qux OPTION_FROM_SET=bar java -jar target/server.jar
```


### Packaging as war

`lein ring uberwar`

## License

Distributed under the Eclipse Public License, the same as Clojure.
