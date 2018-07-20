g8js
====

This is port of great tool [giter8][giter8] for Node.js

## Prerequisite

* Node.js (version 8+)
* Git (You need to invoke `git` command from your terminal)

## Installation

Clone this repository and copy files in `bin` folder to where you like.

In windows, `g8js.cmd` is your launcher. For mac/linux `g8js` is the one.

## Usage

```
$ g8js --help
g8js 0.0.1
Usage: g8js [options] <template>

  --help                   show this help message
  <template>               github user/repo
  -o, --out <value>        output directory
  -D, --no-generate        never generate files (`git clone` will be executed anyway)
  --params key1=value1,key2=value2...
                           additional key-value args (prior to defaults)
```

To generate template:

```
$ g8js scala/scala-seed.g8
```

To see what files are processed (without generating anything):

```
$ echo %cd%
C:\dev\scala\github\g8js
$ g8js scala/scala-seed.g8 -D
name [Scala Seed Project]:
description [A minimal Scala project.]:
'--no-generate' flag found.
Rendering template: C:\Users\lettenj61\_g8js\scala-seed.g8\src\main\g8\build.sbt => C:\dev\scala\github\g8js\scala-seed-project\build.sbt
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\project
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src
Rendering template: C:\Users\lettenj61\_g8js\scala-seed.g8\src\main\g8\project\build.properties => C:\dev\scala\github\g8js\scala-seed-project\project\build.properties
Rendering template: C:\Users\lettenj61\_g8js\scala-seed.g8\src\main\g8\project\Dependencies.scala => C:\dev\scala\github\g8js\scala-seed-project\project\Dependencies.scala
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\main
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\test
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\main\scala
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\test\scala
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\main\scala\example
Creating directory: C:\dev\scala\github\g8js\scala-seed-project\src\test\scala\example
Rendering template: C:\Users\lettenj61\_g8js\scala-seed.g8\src\main\g8\src\main\scala\example\Hello.scala => C:\dev\scala\github\g8js\scala-seed-project\src\main\scala\example\Hello.scala
Successfully generated: C:\dev\scala\github\g8js\scala-seed-project
```

## Functions

`g8js` runs `git clone` on specified repository, then process their contents as template and render them in output location.

The template syntax is simulating original `giter8` implementation. ([Documentation][g8docs])

`g8js` will cache templates in cache directory, which defaults to `~/_g8js` (in windows, `%USERPROFILE%\_g8js`).

### Notice

* It doesn't have `giter8` feature to resolve library version from Maven Central (maven property).
* It only supports templates hosted on GitHub.

## Highlights

* Not-so-heavy executable (385+ kb)
* No NPM dependency is required

## Proposals

* Clean / update cached template
* Support non GitHub projects, file URIs.

## License

The original `giter8` project is licensed under Apache Software License 2.0 .
`g8js` is licensed under MIT license.

[giter8]: https://github.com/foundweekends/giter8
[g8docs]: http://www.foundweekends.org/giter8/
