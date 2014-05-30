This README is just a quick overview of the Skream project.

Skream
======
Skream is a high-performance time-series library with memory-footprint guarantees. The name is short for "online SKetching and stREAMing," and is pronounced like "scream" in English. For scalability, Skream is written in Clojure and has zero side-effects. The library includes a simple RESTful web-service for clients.

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

License
=======
Skream is released under the Eclipse Public License, so you can easily incorporate the library into your commercial or non-commercial projects.

Version
=======
0.1 Alpha

Architecture
============
Skream is a [Leiningen](http://leiningen.org/) Clojure project with decent automated test coverage. The main data-structure are simple Clojure maps with sequential updates handled by functions in map metadata.

Performance
===========
Everything is done without side-effects, in-memory, and with only the minimal amount of state. This provides fundamental scalability across large time-series. Side-effect-less updates are done in parallel, utilizing every core (CPU) on the server.


