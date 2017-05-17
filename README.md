# Cordova-Plugin-Bluetooth-Printer
A cordova plugin for bluetooth printer for android platform, which support text printing and POS printing.

##Support
- Text
- POS Commands
- Image Printing (todo)
- Barcode Printing (todo)
- QRCode Printing (on work)


##Tested with:
58mm POS-5802LD

##Install
Using the Cordova CLI and NPM, run:

```
cordova plugin add https://github.com/srehanuddin/Cordova-Plugin-Bluetooth-Printer.git
```



##Usage
Get list of paired bluetooth printers

```
BTPrinter.list(function(data){
        console.log("Success");
        console.log(data); //list of printer in data array
    },function(err){
        console.log("Error");
        console.log(err);
    })
```


Connect printer

```
BTPrinter.connect(function(data){
	console.log("Success");
	console.log(data)
},function(err){
	console.log("Error");
	console.log(err)
}, "PrinterName")
```


Disconnect printer

```
BTPrinter.disconnect(function(data){
	console.log("Success");
	console.log(data)
},function(err){
	console.log("Error");
	console.log(err)
}, "PrinterName")
```


Disconnect printer

```
BTPrinter.disconnect(function(data){
	console.log("Success");
	console.log(data)
},function(err){
	console.log("Error");
	console.log(err)
})
```


Print simple string

```
BTPrinter.printText(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "String to Print")
```


Print image

```
BTPrinter.printImage(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "Image Base64 String")
```

POS printing

```
BTPrinter.printPOSCommand(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, "0C")
//OC is a POS command for page feed
```

QRCode printing

```
BTPrinter.printQRCode(function(data){
    console.log("Success");
    console.log(data)
},function(err){
    console.log("Error");
    console.log(err)
}, qrData)