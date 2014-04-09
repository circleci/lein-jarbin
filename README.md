# lein-jarbin

A lein plugin to run binaries contained a jar.

## Usage

Put `[lein-jarbin "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your project.clj.

    $ lein jarbin [foo/bar "1.2.3"] bbq
    $ lein jarbin foo-bar-1.2.3.jar bbq
    $ lein jarbin . bbq

Will run the script in resources/bin, in the jar, or the local project source tree.

Extra args can be included on the command line:

    $ lein jarbin [foo/bar "1.2.3"] bbq foo 2 3

Environment variables can be specified in the project.clj:

    :jarbin {:bin-dir "bin" ;; relative to resources
             :scripts {:bbq {:env {:foo "bar"
                                   :name :lein/name
                                   :version :lein/version
                                   :JVM_OPTS :lein/jvm-opts}}}

Environment variables with the 'lein' namespace, like :lein/name will take their values from the same key in the project.clj

## License

Copyright © 2014 CircleCI

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
