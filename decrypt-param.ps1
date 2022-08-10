#!/usr/bin/env pwsh

[CmdletBinding(PositionalBinding = $false)]

param (
    [Parameter(Mandatory = $true)][String]    
    $text,

    [Parameter(Mandatory = $false)][String]
    $profileName
)

$scriptDir = Split-Path $script:MyInvocation.MyCommand.Path

if ($IsLinux) {
    $binFolder = "${scriptDir}/bin"
    $binPath = "${binfolder}/bb"
    $bbUrl = "https://github.com/babashka/babashka/releases/download/v0.9.161/babashka-0.9.161-linux-amd64-static.tar.gz"
}
elseif ($IsMacOS) {
    $binFolder = "${scriptDir}/bin"
    $binPath = "${binfolder}/bb"
    $bbUrl = "https://github.com/babashka/babashka/releases/download/v0.9.161/babashka-0.9.161-macos-amd64.tar.gz"
}
else {
    $binFolder = "${scriptDir}/bin"
    $binPath = "${binfolder}/bb.exe"
    $bbUrl = "https://github.com/babashka/babashka/releases/download/v0.9.161/babashka-0.9.161-windows-amd64.zip"
}

function ensureBbExists () {
    if (-not (Test-Path $binPath)) {
        try {
            write-host "Babashka is not found, downloading from ${bbUrl}"

            $ext = If ($IsLinux -or $IsMacOS) { "tar.gz" } Else { "zip" }
            $outFile = "${binPath}.${ext}"

            ni -Path $binFolder -ItemType Directory -Force
            irm $bbUrl -OutFile $outFile

            if ($IsLinux -or $IsMacOS) {
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

& "${fullBbPath}/bb" --config "${scriptDir}/bb.edn" prepare

$params = @("-text", $text) 
if ($profileName) { $params += "-profile"; $params += $profileName }

& "${fullBbPath}/bb" --config "${scriptDir}/bb.edn" run decrypt-param @params
