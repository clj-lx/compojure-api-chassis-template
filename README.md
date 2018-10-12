# compojure-api-chassis leiningen template

A Leiningen template for compojure-api microservices chassis.

### Core

These components cannot be swapped out.

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
* reloaded workflow through `mount` and `dev/user.clj`   
  
### Optional

These are optional. 

* Friend & buddy for oauth & auth
 * https://github.com/clojusc/friend-oauth2
 * https://github.com/funcool/buddy-auth
* db migrations (migratus & migratus-lein)
 * https://github.com/yogthos/migratus#quick-start-leiningen-2x
* Selmer for html rendering
 * https://github.com/yogthos/Selmer
* tracing TODO

## Usage

> lein new compojure-api-chassis myapp +pgsql +html +oauth2

