; NSIS Installer Script for Watchio IPTV
; This script creates a Windows installer that installs the application
; to Program Files and creates shortcuts.

;--------------------------------
; Includes

!define APP_EXE "Watchio IPTV.exe"

!include "MUI2.nsh"
!include "FileFunc.nsh"

;--------------------------------
; General

; Name and file
Name "Watchio IPTV"
OutFile "Watchio IPTV-windows-setup.exe"
Unicode True

; Default installation folder
InstallDir "$PROGRAMFILES64\Watchio IPTV"

; Get installation folder from registry if available
InstallDirRegKey HKCU "Software\Watchio IPTV" ""

; Request application privileges for Windows Vista/7/8/10/11
RequestExecutionLevel admin

; Version information
VIProductVersion "0.0.1.0"
VIAddVersionKey "ProductName" "Watchio IPTV"
VIAddVersionKey "Comments" "A modern IPTV player application"
VIAddVersionKey "CompanyName" "Watchio IPTV"
VIAddVersionKey "LegalCopyright" "Copyright © 2026"
VIAddVersionKey "FileDescription" "Watchio IPTV Installer"
VIAddVersionKey "FileVersion" "0.0.1.0"
VIAddVersionKey "ProductVersion" "0.0.1.0"

;--------------------------------
; Interface Settings

!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

;--------------------------------
; Pages

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "license.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

;--------------------------------
; Languages

!insertmacro MUI_LANGUAGE "English"

;--------------------------------
; Installer Sections

Section "Watchio IPTV" SecMain

  SectionIn RO
  
  ; Set output path to the installation directory

  SetOutPath "$INSTDIR"
  
  ; Copy all files from the build directory
  ; Note: In GitHub Actions, we're in windows/ directory, so we go up one level
  File /r /x "*.pdb" /x "Watchio IPTV-windows-*" "..\build\windows\x64\runner\Release\*"
  
  ; Store installation folder
  WriteRegStr HKCU "Software\Watchio IPTV" "" $INSTDIR
  
  ; Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  
  ; Add to Add/Remove Programs
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                   "DisplayName" "Watchio IPTV"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
  "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                   "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                 "DisplayIcon" "$INSTDIR\${APP_EXE}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                   "Publisher" "Watchio IPTV"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                   "DisplayVersion" "1.3.0"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                     "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV" \
                     "NoRepair" 1

SectionEnd

Section "Start Menu Shortcuts" SecStartMenu

  ; Create shortcuts
  CreateDirectory "$SMPROGRAMS\Watchio IPTV"
  CreateShortcut "$SMPROGRAMS\Watchio IPTV\Watchio IPTV.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
  CreateShortcut "$SMPROGRAMS\Watchio IPTV\Uninstall.lnk" "$INSTDIR\Uninstall.exe"

SectionEnd

Section "Desktop Shortcut" SecDesktop

  CreateShortcut "$DESKTOP\Watchio IPTV.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0


SectionEnd

;--------------------------------
; Descriptions

; Language strings
LangString DESC_SecMain ${LANG_ENGLISH} "Install Watchio IPTV application files."
LangString DESC_SecStartMenu ${LANG_ENGLISH} "Create Start Menu shortcuts."
LangString DESC_SecDesktop ${LANG_ENGLISH} "Create a desktop shortcut."

; Assign language strings to sections
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMain} $(DESC_SecMain)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStartMenu} $(DESC_SecStartMenu)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop} $(DESC_SecDesktop)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

;--------------------------------
; Uninstaller Section

Section "Uninstall"

  ; Remove files and uninstaller
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR"
  
  ; Remove shortcuts, if any
  Delete "$SMPROGRAMS\Watchio IPTV\Watchio IPTV.lnk"
  Delete "$SMPROGRAMS\Watchio IPTV\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Watchio IPTV"
  Delete "$DESKTOP\Watchio IPTV.lnk"
  
  ; Remove registry keys
  DeleteRegKey /ifempty HKCU "Software\Watchio IPTV"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Watchio IPTV"

SectionEnd

