g8js
====

This is port of great tool [giter8][giter8] for Node.js

## Prerequisite

* Node.js (version 8+)
* Git (You need to invoke `git` command from your terminal)

## Installation

Clone this repository and copy files in `bin` folder wherever you like.

Set `PATH` environment variable for exeutables.

In windows, `g8js.cmd` is your launcher. For mac/linux `g8js` is the one.

## Usage

```
$ g8js --help
g8js 0.0.1
Usage: g8js [options] <template>

  --help                   show this help message
  <template>               github user/repo
  -H, --host [hostname]    hostname for template repository (default: 'github.com')
  -o, --out [path]         output directory
  -D, --no-generate        never generate files (`git clone` will be executed anyway)
  -v, --verbose            verbose output
  -y, --yes                no prompt and use defaults ('-p' options are respected)
  -p, --props key1=value1,key2=value2...
                           additional key-value args (prior to defaults)
```

To generate template:

```
$ g8js scala/scala-seed.g8
```

To see what files are processed (without generating anything):

```
$ echo %cd%
C:\dev\scala
$ g8js scala/scala-seed.g8 --no-generate
name [Scala Seed Project]:
description [A minimal Scala project.]:
'--no-generate' flag found.

RENDER:
C:\Users\lettenj61\_g8js\github.com\scala-seed.g8\src\main\g8\build.sbt => C:\dev\scala\scala-seed-project\build.sbt
MKDIR: C:\dev\scala\scala-seed-project\project
MKDIR: C:\dev\scala\scala-seed-project\src
RENDER:
C:\Users\lettenj61\_g8js\github.com\scala-seed.g8\src\main\g8\project\build.properties => C:\dev\scala\scala-seed-project\project\build.properties
RENDER:
C:\Users\lettenj61\_g8js\github.com\scala-seed.g8\src\main\g8\project\Dependencies.scala => C:\dev\scala\scala-seed-project\project\Dependencies.scala
MKDIR: C:\dev\scala\scala-seed-project\src\main
MKDIR: C:\dev\scala\scala-seed-project\src\test
MKDIR: C:\dev\scala\scala-seed-project\src\main\scala
MKDIR: C:\dev\scala\scala-seed-project\src\test\scala
MKDIR: C:\dev\scala\scala-seed-project\src\main\scala\example
MKDIR: C:\dev\scala\scala-seed-project\src\test\scala\example
RENDER:
C:\Users\lettenj61\_g8js\github.com\scala-seed.g8\src\main\g8\src\main\scala\example\Hello.scala => C:\dev\scala\scala-seed-project\src\main\scala\example\Hello.scala
Successfully generated: C:\dev\scala\github\g8js\scala-seed-project
```

### Specifying properties

Properties used to render template is collected in some ways.

By default `g8js` asks you to input values for each variables defined in that template via prompt.

Alternatively you can pass values with command line option `--props, -p`. This form is prior to anything so `g8js` would never show prompt with the property key specified in `-p` options.

You can specify property values in syntax like `-p name=CoolProject,organization=foobar.com`, and `-p` option can occur many times.

For example, given options below:

```
$ g8js nowhere/template.g8 -p name=foo,organization=bar -p level=99 -p scalaVersion=2.11.12
```

Then `g8js` parses them like:

```scala
val props = Map(
  "name" -> "foo",
  "organization" -> "bar",
  "level" -> "99",
  "scalaVersion" -> "2.11.12"
)
```

When there is no value from cli option, and you skip prompt by pressing `Enter` with blank input, values defined in templates' `default.properties` will be used.

Lastly you can tell `g8js` to skip all prompts and use default values by giving `-y, --yes` flag. Note that even if you use `-y`, the `-p` options will be respected, and prior to templates' defaults.

## Functions

`g8js` runs `git clone` on specified repository, then process their contents as template and render it to output location.

The template syntax is simulating original `giter8` implementation. ([Original documentation][g8docs])

`g8js` will cache templates in local directory, grouping by hostname, which defaults to `~/_g8js` (in windows, `%USERPROFILE%\_g8js`). For example `lettenj61/scala-seed.g8` with `--host github.com` will cached under `~/_g8js/github.com/lettenj61/scala-seed.g8`.

## Limitations

* It doesn't have `giter8` feature to resolve library version from Maven Central (maven property).
* It doesn't support conditionals in template syntax.
* It doesn't support `g8ignore` and `.gitignore`.

## Highlights

* Not-so-heavy executable (~400kb minified)
* No NPM dependency is required

## Todo

* Clean / update cached template
* Let user choose use cache or not
* Specify which branch to pull template
* Support full URLs, file URIs
* Support conditional syntax

## License

The original `giter8` project is licensed under Apache Software License 2.0.

`g8js` is licensed under MIT license.

[giter8]: https://github.com/foundweekends/giter8
[g8docs]: http://www.foundweekends.org/giter8/
