# AzaharInterceptor

This is mainly for use with iiSU but may work elsewhere.

## Features

- Launching Internal Content
- Launching Roms as passthrough content
- Debugging Info for in case something goes wrong! (Rivoting i know)
- Customization support for the extention

## Supported Emulators

- Azahar
- Azahar Debug
- Azahar Plus

## Requirements

- iiSU emuladores.json update
- iiSU supported_emuladores.json update
- Self made shortcut files

## Installation

Install the Apk and continue to configuration

## Configuration

After installing go to the settings tab and select your Azahar User directory. its the one that has your nand, sdmc, load, and shaders for example.

after that you need to append these to your iiSU files; the 1st and 3rd are appended below AZAHARPLUS:

### emulators.json (application)
    {
      "id": "AZAHARINTERCEPTOR",
      "name": "Azahar Interceptor",
      "routeType": "uri",
      "commands": [
        {
          "description": "Azahar Interceptor",
          "command": "com.zeninari.azaharinterceptor/.MainActivity --activity-clear-task --activity-clear-top -a android.intent.action.VIEW -d %ROM_URI%"
        }
      ],
        "packages": [
          "com.zeninari.azaharinterceptor"
      ]
    },

### emulators.json (extention)
The Below is an example using the default config:

      "longName": "Nintendo 3DS",
      "releaseYear": "2011",
      "releaseDate": "2011-02-26",
      "manufacturer": "Nintendo",
      "retroAchievementsId": "NA",
      "romExtensions": [
        ".3ds",
        ".cia",
        ".3DS",
        ".3dsx",
        ".3DSX",
        ".app",
        ".APP",
        ".axf",
        ".AXF",
        ".cci",
        ".CCI",
        ".cxi",
        ".CXI",
        ".elf",
        ".ELF",
        ".z3dsx",
        ".Z3DSX",
        ".zcci",
        ".ZCCI",
        ".zcxi",
        ".ZCXI",
        ".7z",
        ".7Z",
        ".zip",
        ".ZIP",
        ".zcia",
        ".ZCIA",
        ".n3ds", <- new extention
        ".N3DS"  <- new extention
      ],

### supported_emulators.json
    {
      "name": "Azahar Interceptor",
      "packages": [
        "com.zeninari.azaharinterceptor"
      ]
    },

### Shortcuts
after doing all that you can now make your .shortcut! the default is .n3ds and it expects the 16 character Title ID.

*For example if you want the home menu* :

USA - 0004003000008F02\
EUR - 0004003000009802\
JAP - 0004003000008202\
CHN - 000400300000A102\
KOR - 000400300000A902\
TWN - 000400300000B102

and you put the ID inside the file in plain text basically think of it similar to .steam files or .Psvita files (renamed .txt files)

if you want to know what i personally use to name my files:

Name (Serial) (Region).n3ds

So For My Home Menu:\
3DS Home Menu (USA-3DS-MENU) (U).n3ds containing 0004003000008F02

### iiSU
make sure to add the interceptor as your EMU of choice by hovering over 3DS and pressing:

Select -> Edit Console Settings -> Emulator -> Choose Emulator -> Azahar Interceptor -> Summary -> Update Console

## Usage

It just works after setup no extra permissions or anything else needs to be touched! nifty!


## How It Works

You supply it a shortcut file with the TITLE ID of the 3DS Game or app inside of it. there is no automatic way of generating these.

it can be anyname+any.extention just make sure to configure whatever extention you want to use in the settings page. also don't use an already existing rom format. 


## Credits

Made by Zeninari with Assistance from AI for explaining certain things and helping with basic examples and usage examples

## Disclaimer

I Know that use of AI is heavily criticized, so use if you want to use it. i wont deny that i used it in a certain way so up to you how you see this project.
