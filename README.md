# lein-jarbin

A lein plugin to run binaries contained a jar.

This can be used for e.g. complicated system startup procedures, or daemonizing. Think of it as similar to lein-init-script, but without the installation process, or lein-daemon but less opinionated (and more reliable!).


## Usage

`:plugins [[circleci/lein-jarbin "0.1.0"]]`

    $ lein jarbin [foo/bar "1.2.3"] bbq.sh
    $ lein jarbin ./foo-bar-1.2.3.jar bbq.sh

Will run the script in resources/bin/bbq.sh, or in the jar.

In the first usage, jarbin will download the jar if necessary. The jar should be resolvable from standard jar locations, or add a repo to your ~/.lein/profiles.clj

The second usage takes a path to a local jar on the filesystem. The third usage is for running from the source tree.

Extra args can be included on the command line:

    $ lein jarbin [foo/bar "1.2.3"] bbq foo 2 3

Environment variables can be specified in the project.clj:

    :jarbin {:bin-dir "bin" ;; relative to resources. optional, defaults to "bin",
             :scripts {"bbq.sh" {:env {:foo "bar"
                                       :name :lein/name
                                       :version :lein/version
                                       :JVM_OPTS :lein/jvm-opts}}}

Environment variables with the 'lein' namespace, like :lein/name will take their values from the same key in the project.clj

There are a few more 'special' env vars:
 - :jarbin/coord, the coordinate passed to jarbin, i.e. "[foo/bar 1.2.3]"
 - :jarbin/jar-path, the path to the resolved jar

## Limitations
Jarbin creates a temp directory, containing your project.clj and the script. Due to races, and jarbin not knowing when you're 'done' with the code, it never cleans up the temp directory.

## See Also

lein-jartask. Very similar, but runs lein tasks, rather than scripts

## License

Copyright © 2014 CircleCI

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
