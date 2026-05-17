$ErrorActionPreference = "Stop"

$JavaHome    = "A:\JDK8"
$WtkHome     = "A:\Java_ME_platform_SDK_3.0"
$SrcDir      = "midlet\src"
$BuildDir    = "build"
$PreverifyDir = "preverified"
$OutputDir   = "dist"
$IconSrc     = "midlet\src\icon.png"

Write-Host "Cleaning up old files..."
if (Test-Path $BuildDir)     { Remove-Item -Recurse -Force $BuildDir }
if (Test-Path $PreverifyDir) { Remove-Item -Recurse -Force $PreverifyDir }
if (Test-Path $OutputDir)    { Remove-Item -Recurse -Force $OutputDir }

New-Item -ItemType Directory -Force -Path $BuildDir      | Out-Null
New-Item -ItemType Directory -Force -Path $PreverifyDir  | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir     | Out-Null

Write-Host "Compiling Java code..."
& "$JavaHome\bin\javac.exe" -source 1.3 -target 1.3 `
    -bootclasspath "$WtkHome\lib\cldc_1.1.jar;$WtkHome\lib\midp_2.0.jar" `
    -d $BuildDir (Get-ChildItem -Path $SrcDir -Filter "*.java").FullName

Write-Host "Preverifying classes..."
& "$WtkHome\bin\preverify.exe" `
    -classpath "$WtkHome\lib\cldc_1.1.jar;$WtkHome\lib\midp_2.0.jar" `
    -d $PreverifyDir $BuildDir

# Copy icon into preverified folder so it gets bundled inside the JAR
if (Test-Path $IconSrc) {
    Write-Host "Including app icon..."
    Copy-Item $IconSrc "$PreverifyDir\icon.png"
}

Write-Host "Generating Manifest..."
$ManifestPath = "$BuildDir\MANIFEST.MF"
@"
Manifest-Version: 1.0
MIDlet-1: KeypadMessenger, /icon.png, KeypadMessengerMidlet
MIDlet-Name: KeypadMessenger
MIDlet-Vendor: Developer
MIDlet-Version: 1.0
MIDlet-Icon: /icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
"@ | Out-File -FilePath $ManifestPath -Encoding ASCII

Write-Host "Packaging JAR..."
& "$JavaHome\bin\jar.exe" cfm "$OutputDir\KeypadMessenger.jar" $ManifestPath -C $PreverifyDir .

Write-Host "Generating JAD file..."
$JarFile = Get-Item "$OutputDir\KeypadMessenger.jar"
$JadPath = "$OutputDir\KeypadMessenger.jad"
@"
MIDlet-1: KeypadMessenger, /icon.png, KeypadMessengerMidlet
MIDlet-Name: KeypadMessenger
MIDlet-Vendor: Developer
MIDlet-Version: 1.0
MIDlet-Icon: /icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MIDlet-Jar-URL: KeypadMessenger.jar
MIDlet-Jar-Size: $($JarFile.Length)
"@ | Out-File -FilePath $JadPath -Encoding ASCII

Write-Host "---------------------------------------------------"
Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "Your files are ready in the '$OutputDir' folder:"
Write-Host "  - KeypadMessenger.jar  (install this on the phone)"
Write-Host "  - KeypadMessenger.jad  (descriptor file)"
Write-Host "---------------------------------------------------"
