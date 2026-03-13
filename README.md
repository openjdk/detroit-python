# Project Detroit Python - a javax.script engine for Python

This branch requires Python 3.14.

Install pyenv <https://github.com/pyenv/pyenv> and
pyenv-virtualenv <https://github.com/pyenv/pyenv-virtualenv>
for your OS. Using pyenv and pyenv-virtualenv, you can
install specific Python versions and create virtual environments within.

After installing pyenv and configuring your shell's environment variables
(like ~/.zshrc or ~/.bashrc), you must restart your terminal session for
the changes to take effect.

You can create install Python 3.14, create a virtual environment and
activate it using the following commands:

```sh
pyenv install 3.14
pyenv virtualenv 3.14 mypyenv-3.14
pyenv activate mypyenv-3.14
```

* You can skip the next two steps if you do not want to
re-generate jextract binding classes from Python C API.

* Download & extract jextract early access binary from

    <https://jdk.java.net/jextract/>

* Put jextract in PATH

* Install JDK 25 and define JAVA_HOME to be JDK 25 home

* From the current directory

```sh
mvn clean verify surefire-report:report javadoc:javadoc
```

command builds and tests the project

* From the current directory:

```sh
sh jpython.sh
```

runs a simple REPL. or

```sh
sh jpython.sh samples/squares.py
```

runs a sample script file

* To run a java sample that uses Python script engine:

```sh
sh run.sh samples/Hello.java
```
