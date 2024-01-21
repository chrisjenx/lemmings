# Lemmings

This is used by CI (or locally) to manage and spin up hundreds of emulators and run tests on a single instance.

ADB and Emulator tools don't handle multi process environments, to get around this, we need to provide locking 
functionality to handle this. It will wait and grab the next "free" device to test against.

There is an example `script/test-runner` script that would one would run on CI with passed in test apk and 
configuration - your mileage WILL vary (that is example BuildKite script)

# Usage

```
$ java -jar testrunner.jar --help
```

## Example

Here's what the CI will effectively run, it will get most of these values from the environment variables on CI, you 
can pass these in manually if you want to run locally.

```bash
java -jar testrunner.jar \ 
    --apk /artifacts/apk/applicationDebugTest.apk \
    --output /artifacts/results \
    --device "pixel_6" \
    --project "application" \
    --flavor "debug" \
    --systemImg system-images;android-30;google_atd;x86 
```

**Output** will put the test results in `{output}/test-results/*.xml` and the emulator logs in `/{output}/logs/*.log`

### Options

```bash
Options: 
    --apk, -a -> Path to the apk to test (always required) { String }
    --output, -o -> Path for the test run outputs (always required) { String }
    --device, -e [pixel_6] -> Device profile name i.e. pixel_6 { String }
    --project, -p [projectName] -> Project name { String }
    --flavor, -f [flavorName] -> Flavor name { String }
    --debug, -D [false] -> Debug mode 
    --systemImg, -s [system-images;android-30;google_atd;x86] -> System image to use for emulator { String }
    --clean, -c [false] -> Create a clean device before running tests 
    --help, -h -> Usage info 
```

# FAQ

## Gradle Managed Devices

Gradle Managed devices are the future but there are some limitations of which this will still be useful long when
you run directly in Gradle, firstly gradle doens't handle multi process yet and there are bug reports of device locking
issues (https://issuetracker.google.com/issues/287312019).

There are cases in larger projects where it makes sense to build all the APKs and farm them out to a different machine.
I.e. build on cheaper multi tentant machines, then run your UI tests on smaller dedicated hardware.

## Future Ideas

This is a glorified test runner around android sdk `adb` and `emulator` commands. What will be nice is if there is a
host that a custom GradleManagedDevice can talk to from Gradle (using ADB over network) to run tests massively parallel
on a different host. (Much like Firebase Test Lab, but with much more freedom to pick devices and run using actual AVD
images.)