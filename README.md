![Eclair Logo](.readme/logo.png)

[![Build Status](https://travis-ci.org/ACINQ/eclair.svg?branch=master)](https://travis-ci.org/ACINQ/eclair)
[![codecov](https://codecov.io/gh/acinq/eclair/branch/master/graph/badge.svg)](https://codecov.io/gh/acinq/eclair)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Eclair** is a scala implementation of the Lightning Network. It can run with or without a GUI, and a JSON-RPC API is also available.

This software follows the [Lightning Network Specifications (BOLTs)](https://github.com/lightningnetwork/lightning-rfc). Other implementations include [c-lightning](https://github.com/ElementsProject/lightning) and [lnd](https://github.com/LightningNetwork/lnd).
 
 ---
 
 :construction: Both the BOLTs and Lightning itself are still a work in progress. Expect things to break/change!
 
 :rotating_light: If you intend to run Lightning on mainnet:
 - Keep in mind that it is beta-quality software and **don't put too much money** in it
 - Lightning's JSON-RPC API should **NOT** be accessible from the outside world (similarly to QTUM Core API)
 - Specific [configuration instructions for mainnet](#mainnet-usage) are provided below
 
---

## Lightning Network Specification Compliance
Please see the latest [release note](https://github.com/qtumproject/lightning-demo/releases) for detailed information on BOLT compliance.

## Overview

![Eclair Demo](.readme/screen-1.png)

## JSON API

Eclair offers a feature rich HTTP API that enables application developers to easily integrate.

For more information please visit the [API documentation website](https://acinq.github.io/eclair).

:warning: You can still use the old API by setting the `eclair.api.use-old-api=true` parameter, but it is now deprecated and will soon be removed. The old documentation is still available [here](OLD-API-DOCS.md).

## Installation

### Configuring QTUM Core

:warning: Lightning requires QTUM Core 0.17.0 or higher. If you are upgrading an existing wallet, you need to create a new address and send all your funds to that address.

Lightning needs a _synchronized_, _segwit-ready_, **_zeromq-enabled_**, _wallet-enabled_, _non-pruning_, _tx-indexing_ [QRUM Core](https://github.com/qtumproject/qtum) node. 
Lightning will use any QTUM it finds in the QTUM Core wallet to fund any channels you choose to open. Lightning will return QTUM from closed channels to this wallet.

Run bitcoind with the following minimal `qtum.conf`:
```
server=1
rpcuser=foo
rpcpassword=bar
txindex=1
addresstype=bech32
zmqpubrawblock=tcp://127.0.0.1:29000
zmqpubrawtx=tcp://127.0.0.1:29000
```

### Installing Lightning

Lightning is developed in [Scala](https://www.scala-lang.org/), a powerful functional language that runs on the JVM, and is packaged as a JAR (Java Archive) file. We provide 2 different packages, which internally use the same core libraries:
* eclair-node, which is a headless application that you can run on servers and desktops, and control from the command line
* eclair-node-gui, which also includes a JavaFX GUI

To run Lightning, you first need to install Java, we recommend that you use [OpenJDK 11](https://jdk.java.net/11/). Lightning will also run on Oracle JDK 1.8, Oracle JDK 11, and other versions of OpenJDK but we don't recommend using them.

Then download the latest fat jar and depending on whether or not you want a GUI run the following command:
* with GUI:
```shell
java -jar eclair-node-gui-<version>-<commit_id>.jar
```
* without GUI:
```shell
java -jar eclair-node-<version>-<commit_id>.jar
```

### Configuring Lightning

#### Configuration file

Lighting reads its configuration file, and write its logs, to `~/.qtum-eclair` by default.

To change your node's configuration, create a file named `eclair.conf` in `~/.qtum-eclair`. Here's an example configuration file:

```
eclair.node-alias=eclair
eclair.node-color=49daaa
```

Here are some of the most common options:

name                         | description                                                                           | default value
-----------------------------|---------------------------------------------------------------------------------------|--------------
 eclair.chain                | Which blockchain to use: *regtest*, *testnet* or *mainnet*                            | mainnet
 eclair.server.port          | Lightning TCP port                                                                    | 9735
 eclair.api.enabled          | Enable/disable the API                                                                | false. By default the API is disabled. If you want to enable it, you must set a password.
 eclair.api.port             | API HTTP port                                                                         | 8080
 eclair.api.password         | API password (BASIC)                                                                  | "" (must be set if the API is enabled)
 eclair.bitcoind.rpcuser     | Bitcoin Core RPC user                                                                 | foo
 eclair.bitcoind.rpcpassword | Bitcoin Core RPC password                                                             | bar
 eclair.bitcoind.zmqblock    | Bitcoin Core ZMQ block address                                                        | "tcp://127.0.0.1:29000"
 eclair.bitcoind.zmqtx       | Bitcoin Core ZMQ tx address                                                           | "tcp://127.0.0.1:29000"
 eclair.gui.unit             | Unit in which amounts are displayed (possible values: msat, sat, bits, mbtc, btc)     | btc

Quotes are not required unless the value contains special characters. Full syntax guide [here](https://github.com/lightbend/config/blob/master/HOCON.md).

&rarr; see [`reference.conf`](eclair-core/src/main/resources/reference.conf) for full reference. There are many more options!

#### Java Environment Variables

Some advanced parameters can be changed with java environment variables. Most users won't need this and can skip this section.

:warning: Using separate `datadir` is mandatory if you want to run **several instances of lightning** on the same machine. You will also have to change ports in `eclair.conf` (see above).

name                  | description                                | default value
----------------------|--------------------------------------------|--------------
eclair.datadir        | Path to the data directory                 | ~/.qtum-eclair
eclair.headless       | Run eclair without a GUI                   | 
eclair.printToConsole | Log to stdout (in addition to eclair.log)  |

For example, to specify a different data directory you would run the following command:
```shell
java -Declair.datadir=/tmp/node1 -jar lightning-capsule.jar
```

#### Logging

Eclair uses [`logback`](https://logback.qos.ch) for logging. To use a different configuration, and override the internal logback.xml, run:

```shell
java -Dlogback.configurationFile=/path/to/logback-custom.xml -jar eclair-node-gui-<version>-<commit_id>.jar
```

#### Backup

The files that you need to backup are located in your data directory. You must backup:
- your seed (`seed.dat`)
- your channel database (`eclair.sqlite.bak` under directory `mainnet`, `testnet` or `regtest` depending on which chain you're running on)

Your seed never changes once it has been created, but your channels will change whenever you receive or send payments. Eclair will
create and maintain a snapshot of its database, named `eclair.sqlite.bak`, in your data directory, and update it when needed. This file is 
always consistent and safe to use even when Eclair is running, and this is what you should backup regularly.

For example you could configure a `cron` task for your backup job. Or you could configure an optional notification script to be called by eclair once a new database snapshot has been created, using the following option:
```
eclair.backup-notify-script = "/absolute/path/to/script.sh"
```
Make sure that your script is executable and uses an absolute path name for `eclair.sqlite.bak`.

Note that depending on your filesystem, in your backup process we recommend first moving `eclair.sqlite.bak` to some temporary file 
before copying that file to your final backup location.


## Docker

```
cd lightning-demo
docker build -t qtum-eclair-img .
```

You can use the `JAVA_OPTS` environment variable to set arguments to `eclair-node`.

```
docker run -ti --rm -e "JAVA_OPTS=-Xmx512m -Declair.api.binding-ip=0.0.0.0 -Declair.node-alias=node-pm -Declair.printToConsole" qtum-eclair-img
```

If you want to persist the data directory, you can make the volume to your host with the `-v` argument, as the following example:

```
docker run -ti --rm -v "/path_on_host:/data" -e "JAVA_OPTS=-Declair.printToConsole" qtum-eclair-img
```

## Plugins

For advanced usage, Eclair supports plugins written in Scala, Java, or any JVM-compatible language.

A valid plugin is a jar that contains an implementation of the [Plugin](eclair-node/src/main/scala/fr/acinq/eclair/Plugin.scala) interface.

Here is how to run Eclair with plugins:
```shell
java -jar eclair-node-<version>-<commit_id>.jar <plugin1.jar> <plugin2.jar> <...>
```

## Testnet usage

Eclair is configured to run on mainnet by default, but you can still run it on testnet (or regtest): start your Bitcoin Node in
 testnet mode (add `testnet=1` in `bitcoin.conf` or start with `-testnet`), and change Eclair's chain parameter and Bitcoin RPC port:

```
eclair.chain=testnet
eclair.bitcoind.rpcport=18332
```
Following are the minimum configuration files you need to use for QTUM Core and Lightning.

You may also want to take advantage of the new configuration sections in `bitcoin.conf` to manage parameters that are network specific, 
so you can easily run your bitcoin node on both mainnet and testnet. For example you could use:
### QTUM Core configuration

```
server=1
txindex=1
addresstype=bech32
[main]
rpcuser=<your-mainnet-rpc-user-here>
rpcpassword=<your-mainnet-rpc-password-here>
zmqpubrawblock=tcp://127.0.0.1:29000
zmqpubrawtx=tcp://127.0.0.1:29000
[test]
rpcuser=<your-testnet-rpc-user-here>
rpcpassword=<your-testnet-rpc-password-here>
zmqpubrawblock=tcp://127.0.0.1:29001
zmqpubrawtx=tcp://127.0.0.1:29001
```

## Resources
- [1] [The Bitcoin Lightning Network: Scalable Off-Chain Instant Payments](https://lightning.network/lightning-network-paper.pdf) by Joseph Poon and Thaddeus Dryja
- [2] [Reaching The Ground With Lightning](https://github.com/ElementsProject/lightning/raw/master/doc/deployable-lightning.pdf) by Rusty Russell
- [3] [Lightning Network Explorer](https://explorer.acinq.co) - Explore testnet LN nodes you can connect to
