[CmdletBinding(PositionalBinding = $false)]

param (
    [Parameter(Mandatory = $true)][String]
    $env,

    [Parameter(Mandatory = $true)][String]
    $configPath,

    [Parameter(Mandatory = $false)][String]
    $backupDir = "./backups",

    [Parameter(Mandatory = $false)][String]
    $decryptRegion,

    [Switch][Parameter(Mandatory = $false)][Boolean]
    $dump = $false
)

$scriptDir = Split-Path $script:MyInvocation.MyCommand.Path

if ($isLinux) {
    $binFolder = "${scriptDir}/bin"
    $binPath = "${binfolder}/bb"
    $bbUrl = "https://github.com/babashka/babashka/releases/download/v0.8.2/babashka-0.8.2-linux-amd64-static.tar.gz"
}
else {
    $binFolder = "${scriptDir}/bin"
    $binPath = "${binfolder}/bb.exe"
    $bbUrl = "https://github.com/babashka/babashka/releases/download/v0.8.2/babashka-0.8.2-windows-amd64.zip"
}

function ensureBbExists () {
    if (-not (Test-Path $binFolder)) {
        try {
            write-host "Babashka is not found, downloading from ${bbUrl}"

            $ext = If ($isLinux) { "tar.gz" } Else { "zip" }
            $outFile = "${binPath}.${ext}"

            ni -Path $binFolder -ItemType Directory -Force
            irm $bbUrl -OutFile $outFile

            if ($isLinux) {
                tar -xf $outFile -C ${binFolder}
                chmod +x ${binPath}
            }
            else {
                expand-archive -LiteralPath $outFile -DestinationPath $binFolder
            }
            rm $outFile
        }
        catch {
            throw $_.Exception.Message
        }
    }
}

ensureBbExists

$fullBbPath = resolve-path $binFolder

$env:Path += ";${fullBbPath};"

bb --config "${scriptDir}/bb.edn" prepare

$params = @("-env", $env, "-config", $configPath, "-backup-dir", $backupDir)
if ($decryptRegion) { $params += "-decrypt-region"; $params+= $decryptRegion }
if ($dump) { $params += "-dump"; $params+= $dump }

bb --config "${scriptDir}/bb.edn" run sync-params @params
