This README is just a quick overview of the Skream project.

Skream
======
Skream is a high-performance time-series library with memory-footprint guarantees. The name is short for "online SKetching and stREAMing," and is pronounced like "scream" in English. For scalability, Skream is written in Clojure and has zero side-effects. The library includes a simple RESTful web-service for clients.

Artifacts
=======

`skream` artifacts are [released to Clojars](https://clojars.org/clj-time/clj-time).

If you are using Maven, add the following repository definition to your `pom.xml`:

``` xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

The Most Recent Release
=======

With Leiningen:

``` clj
[skream "0.0.2"]
```

With Maven:

``` xml
<dependency>
  <groupId>skream</groupId>
  <artifactId>skream</artifactId>
  <version>0.0.2</version>
</dependency>
```

Features
=======
Skream views a time-series as a simple sequence of numbers. These numbers are read sequentially and in order, with minimal state maintained. These sequences can be sensor readings from an Internet -of- things project, or stock prices in an HFT strategy.

What queries does a Skream currently support?

  - Basic summary statistics (count, minimum, maximum, sum, mean / average)
  - __Variance & standard deviation__
  - __Skewness & kurtosis__
  - Arbitrary __higher-order moments__ (standardized & unstandardized)
  - Range counts
  - __Gaussian range counts__ (e.g. count of elements within 0.42 standard deviations)
  - __Histograms__ (evenly-spaced & Gaussian bins)
  - Exponential moving average
  - Simple moving average
  - Approximate membership (via __Bloom filters__)
  - Approximate individual element counts (via __Count-Min sketches__)
  - Distinct element count (via __HyperLogLog sketches__)
  - Approximate __median__ (via [P2 algorithm](http://www.cs.wustl.edu/~jain/papers/ftp/psqr.pdf))
  - Approximate __arbitrary quantiles__ (e.g. 25% "median")
  - Approximate __mutual information__ between two Skreams (via histograms)

All of these queries are supported with a fixed memory footprint, new numbers added sequentially or in an online sense. The exception is simple moving average queries, which require a window of recent numbers maintained as state.

Example
======

Skream includes a simple command-line interface for experimenting and testing. First clone the Git repository, and then build & run locally using the usual [lein](http://leiningen.org/) executable:

    $ git clone git@github.com:someben/skream.git
    $ cd skream; lein run
    >>> SKREAM 10162

The first header line written to STDERR indicates that a single empty Skream is ready to accept numbers. The header line also reports the PID of the underlying JVM process (e.g. 10162).

Next send a command to tell the Skream which queries to track. For a simple test, just use the "track-default" convenience function that tracks a handful of common, useful statistics. Type the following as STDIN input:

    (track-default *sk*)
    
The `*sk*` earmuffed root variable refers to the process' single Skream. The server responds with the empty Skream, but it is now tracking some statistics and ready for queries:

    {:min nil, [:unstd-moment 2] nil, [:moment 4] nil, [:moment 2] nil, :mean nil, [:win 5] [], :skew nil, :kurt nil, [:unstd-moment 4] nil, [:quantile 0.5] nil, :stdev nil, [:moment 3] nil, :max nil, [:unstd-moment 3] nil, :count 0, :last nil, :sum 0}

The Skream is printed to the console as a Clojure map literal (s-expression). Now you can send a stream of numbers to the same process, by entering numbers as STDIN input one -per- line:

    42.42

The server responds with a Skream that has seen one number:

    {:min 42.42, [:unstd-moment 2] 0, [:moment 4] 0, [:moment 2] 0, :mean 42.42, [:win 5] [42.42], :skew 0, :kurt 0, [:unstd-moment 4] 0, [:quantile 0.5] {:quantile nil}, :stdev 0, :sum-sq-diffs 0, [:moment 3] 0, :median nil, :max 42.42, [:unstd-moment 3] 0, :count 1, :last 42.42, :sum 42.42}

If you pass a number to this interface (e.g. "42.42" above), this is interpreted as a new number to be added to the Skream. In every other case (e.g. the "track-default" function call), the result becomes the new Skream itself.

Next we restart the process, and add a short time-series of recent closing prices of the SPY ETF traded on the NYSE:

    ...hit (Ctrl-C) or (Ctrl-D)
    $ lein run  # from user
    >>> SKREAM 11193
    (track-default *sk*)  ; from user
    {:min nil, ..., :sum 0}
    188.74  ; from user
    {:min 188.74, ..., :sum 188.74}
    187.55  ; from user
    189.13  ; from user
    ...
    195.94  ; from user
    195.88  ; from user
    
The final response from the server process shows some useful statistics that have been calculated on the fly:

    {:min 187.55, [:unstd-moment 2] 157.20218399999857, [:moment 4] 54.60116919834653, [:moment 2] 24.0, :mean 193.17920000000004, [:win 5] [194.83 196.26 196.48 195.94 195.88], :skew -0.6849247938368417, :kurt -0.49252635219653795, [:unstd-moment 4] 2342.5917520379735, [:quantile 0.5] {:quantile 193.72229, :qs [187.55 191.74467 193.72229 195.5992 196.48], :ns [1 7 13 19 25], :dns [0 1/4 1/2 3/4 1], :nps [1 7N 13N 19N 25]}, :stdev 2.5593145566733178, :sum-sq-diffs 157.20218399999857, [:moment 3] -15.123139447917465, :median 193.72229, :max 196.48, [:unstd-moment 3] -253.52042674559698, :count 25, :last 195.88, :sum 4829.48}

Note the higher moment like kurtosis (-0.49ish) and median (about 193.72), estimated without maintaining a full history of the stock prices.

License
=======
Skream is released under the Eclipse Public License, so you can easily incorporate the library into your commercial or non-commercial projects.

Architecture
============
Skream is a [Leiningen](http://leiningen.org/) Clojure project with decent automated test coverage. The main data-structure are simple Clojure maps with sequential updates handled by functions in map metadata.

Performance
===========
Everything is done without side-effects, in-memory, and with only the minimal amount of state. This provides fundamental scalability across large time-series. Side-effect-less updates are done in parallel, utilizing every core (CPU) on the server.


