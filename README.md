# AWS SSM parameters sync utilities

CLI utilities to synchronize `SSM` parameters between different
environment, using configuration files with encryption support:

- sync parameters from config file, with automatic backup
- encrypt text
- decrypt text

## Requirements
`Powershell` and `Java` should be available on the machine to run the script.

## Syncing parameters

In `Powershell` run the following command to synchronize `SSM`
parameters for the `dev01` environment, using configuration file
`dev01.config.yaml`. Backup of existing parameter values for the
environment will be stored under `backups` folder.

``` powershell
./sync-params.ps1 -env dev01 -configPath ./dev01.config.yaml
```

_Note_: There are additional `AWS` profile parameters `-profile` and
`-decryptProfile` (both defaulted to `default`).

### Profile

`-profile` parameter defines `AWS` profile to be used for connecting
to `AWS` and applying `SSM` parameters to. It's a way to configure the
target of `sync-params.ps1`.

### Decrypt profile

`-decryptProfile` parameter defines `AWS` profile to be used to
decrypt the `SecureString` values from the config file.


### Dumping put-parameter AWS CLI commands

There is a convenience CLI key `-dump`, which will print to stdout the
parameters (with decrypted values for `SecureString` parameters) for
specific environment in form of `aws ssm put-parameter <...>` command
statements. This could be useful in the environments where it's not
possible to run the tool, or in manual deployment scenarios.

For example:
``` powershell
./sync-params.ps1 -env dev01 -configPath ./dev01.config.yaml -dump > ./update.dev01.cmd
```
should produce a file `update.dev01.cmd` with the statements: 

``` shell
aws ssm put-parameter --overwrite --name "parameter-name" --type "String" --value "parameter-value"
aws ssm put-parameter --overwrite --name "secure-parameter-name" --type "SecureString" --value "secure-parameter-value"
```

### Configuration file

Configuration file describes the parameters prefix and the parameters
themselves. Each parameter has values for all the environments
supported along with the type of the parameter.

`SecureString` parameters are stored in the configuration file in
encrypted form, so it's fine to have configuration files added to VCS
like Git.

Decryption of such parameter value happens when the parameters are
synced and will use profile supplied in `-decryptProfile` parameter to
access the KMS decryption key for the encrypted value.

For example, the following configuration file, when ran for `stage`
environment

``` yaml
prefix: /app/test-service
parameters:
  Parameter/With/SubValues1:
    type: SecureString
    values:
      dev: AQICAHjyGTSjgHC1vzKuFUGAwMKU21AYkB3x3nl6K3WfiadEOgEGJOtgRy0/oBK1IuJ+9JwOAAAAdzB1BgkqhkiG9w0BBwagaDBmAgEAMGEGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMnu86fMpQGQcDFp5XAgEQgDSkX8nOkpo0g7CWQQo5SsNrm3vN12gDiukreU6FCMOSMbbpB4WIkbKj8Vmp1aVAiGE017MT
      qa: BQICAHjyGTSjgHC1vzKuFUGAwMKU21AYkB3x3nl6K3WfiadEOgEGJOtgRy0/oBK1IuJ+9JwOAAAAdzB1BgkqhkiG9w0BBwagaDBmAgEAMGEGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMnu86fMpQGQcDFp5XAgEQgDSkX8nOkpo0g7CWQQo5SsNrm3vN12gDiukreU6FCMOSMbbpB4WIkbKj8Vmp1aVAiGE017MT
      stage: CQICAHjyGTSjgHC1vzKuFUGAwMKU21AYkB3x3nl6K3WfiadEOgEGJOtgRy0/oBK1IuJ+9JwOAAAAdzB1BgkqhkiG9w0BBwagaDBmAgEAMGEGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMnu86fMpQGQcDFp5XAgEQgDSkX8nOkpo0g7CWQQo5SsNrm3vN12gDiukreU6FCMOSMbbpB4WIkbKj8Vmp1aVAiGE017MT
      prod: DQICAHjyGTSjgHC1vzKuFUGAwMKU21AYkB3x3nl6K3WfiadEOgEGJOtgRy0/oBK1IuJ+9JwOAAAAdzB1BgkqhkiG9w0BBwagaDBmAgEAMGEGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMnu86fMpQGQcDFp5XAgEQgDSkX8nOkpo0g7CWQQo5SsNrm3vN12gDiukreU6FCMOSMbbpB4WIkbKj8Vmp1aVAiGE017MT
  Parameter2:
    type: String
    values:
      dev: dev2-val
      qa: qa2-val
      stage: stage2-val
      prod: prod2-val
      
```

is going to produce the following updates:

```
/app/test-service/stage/Parameter/With/SubValues1 = <DECRYPTED_VALUE>
/app/test-service/stage/Parameter2 = stage2-val
```

### Backup of exsiting SSM parameter values

The script will automatically create a backup file under the folder,
specified by `-backupDir` command line parameter and will do it as a
first step, before updating parameter values from configuration file
and removing old parameters.

The backup file is created for the values for single environment, for
which the sync was initiated.

_Note_: Backed values for `SecureString` are stored in plaintext.

## Encrypting and decrypting `SecureString` values

Before putting sensitive data into `SecureString` parameter it is
neccessary to encrypt it. 

The are two helper scripts `encrypt-param.ps1` and `decrypt-param.ps1`
just for that.

Encryption requires to provide a text to be encrypted, an `AWS`
profile name and `KMS` key. If none provided - `default` value will be
used for profile and the key value will be searched in `~/.aws/config`
file under `ssm-param-files-key` key. Providing the profile/key as an
argument to the script will override the alogrithm.

Below is the example of the profile with `KMS` key set up:
``` ini
[default]
region = us-east-1
output = json
ssm-param-files-key = alias/key-id-to-encrypt-the-values
```

Decryption requires no key to be used (information about it is embedded into the
encrypted payload), but the profile could be still overriden via the parameter.

## Examples

The below command will use the profile `default` to connect to `AWS`
and will use `KMS` key defined in the `default` profile config file
under `ssm-param-files-key` key (if the key is not found in profile
config - the error will throw).

``` powershell
./encrypt-param.ps1 -text "hello world"
```

The below command will use the provided profile value `user2` to
connect to `AWS` and will use `KMS` key defined in the `user2` profile
config file under `ssm-param-files-key` key (if the key is not found
in profile config - the error will throw).

``` powershell
./encrypt-param.ps1 -text "hello world" -profile "user2"
```

To override the `KMS` key in the command above use `-kmsKey` paramter:

``` powershell
./encrypt-param.ps1 -text "hello world" -profile "user2" -kmsKey "alias/some-key-id"
```

The `decrypt-param.ps1` supports only `-profile` key, since the `KSM`
key is already part of the encrypted payload.

The below command synchronizes `SSM` parameters from the supplied
`config.yaml` configuration file. It will use credentials and region
information from `user1` profile to connect to `AWS` and will use
`user2` profile to decrypt `SecureString` values if any:

``` powershell
./sync-params.ps1 -env test -configPath ./config.yaml -profile "user1" -decryptProfile "user2"
```

If there is no need in different profiles and encryption/decryption of
parameter is using `default` profile then the command could be simpler: 

``` powershell
./sync-params.ps1 -env test -configPath ./config.yaml
```
