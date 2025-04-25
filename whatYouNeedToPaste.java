// imports will be provided by android studio itself. 
// You will need to create device.xml file if wanted



public class MainActivity extends AppCompatActivity {
    private UsbManager usbManager;
    private UsbInterface usbInterface;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;
    private UsbDeviceConnection connection;
    private USBDataInterface usbDataInterface; // Add this line
    private Boolean usbConnected = false;
    private volatile boolean isReading = false;

  // these are declared variable which is accessible in scope of appCombatActivity class extention
  // paste following codes in your existing functions , if they exist

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initialize USB Manager
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

    }

  //create new function

  protected void onResume() {
        super.onResume();

        // Register the USB broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED); //ignore this error, it's just a warning APP will work with it

        // Enumerate and request permission for USB devices
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            Log.d("USB_DEVICE", "Found USB Device: " + device.getDeviceName());
            Log.d("USB_DEVICE", "Vendor ID: " + device.getVendorId());
            Log.d("USB_DEVICE", "Product ID: " + device.getProductId());
          
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            );
            if (!usbManager.hasPermission(device)) {
                usbManager.requestPermission(device, permissionIntent);
            } else {
                openUsbConnection(device);
            }
        }}

     @Override
    protected void onPause() {
        super.onPause();
        // Unregister the USB broadcast receiver
        unregisterReceiver(usbReceiver);
    }

   private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("USB", "Received action: " + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Log.d("USB_PERMISSION", "Permission granted: " + permissionGranted);

                if (device != null && permissionGranted) {
                    openUsbConnection(device);
                } else {
                    Log.e("USB_PERMISSION", "Permission denied.");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice detachedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (detachedDevice != null) {
                }
                if (connection != null) {
                    connection.close();
                    usbConnected = false;
                    connection = null;
                }
            }
        }
    };

    private void openUsbConnection(UsbDevice device) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        connection = usbManager.openDevice(device);
        if (connection != null) {
            int vendorId = device.getVendorId();
            int productId = device.getProductId();
//            String deviceName =  String.format("%d:%d", device.getVendorId(), device.getProductId());
//
//            if (deviceName != null) {
//            DrawerActivity drawerActivity = new DrawerActivity();
//            drawerActivity.usbinfo.setText(deviceName);
//            }
//            else {
//                Log.e("USB_CONNECTION", "Device name is null.");
//            }


            Log.d("USB_CONNECTION", "Connected to USB device: Vendor ID=" + vendorId + ", Product ID=" + productId);
        } else {
            Log.e("USB_CONNECTION", "Failed to open USB device connection.");
        }

        usbInterface = device.getInterface(1); 
        connection.claimInterface(usbInterface, true);

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint; // Recieve data
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = endpoint; // For sending data
                }
            }
        }

        if (inEndpoint == null || outEndpoint == null) {
            Log.e("ERROR USB_CONNECTION", "Endpoints not found.");
            return;
        }
        usbConnected = true;
        Log.d("USB CONNECTED BOOLEAN", "usbConnected: " + usbConnected);
        Log.d("USB_CONNECTION", "Endpoints found. In: " + inEndpoint + ", Out: " + outEndpoint);
        Log.d("##1USB_CONNECTION", "##1Connected to USB device: Vendor ID=" + device.getVendorId() + ", ##1Product ID= " + device.getProductId());



//        byte[] rawDescriptors = connection.getRawDescriptors();
//
//        if (rawDescriptors != null) {
//            for (int i = 0; i < rawDescriptors.length; i++) {
//                Log.d("TODAY'S TEST 1####", "Raw Descriptor [" + i + "] (hex): " + String.format("%02X", rawDescriptors[i]));
//                Log.d("TODAY'S TEST 1####", "Raw Descriptor [" + i + "] (decimal): " + rawDescriptors[i]);
//            }
//        } else {
//            Log.d("TODAY'S TEST 1####", "Raw Descriptors: null");
//        }

    }


    // USB DATA HANDLING AND COMMUNICATION
    // PLEASE CONSULT device_filter.xml TO SET product id AND vendor id FIRST
    // IT MAY NEED A CHANGE AT THE FOLLOWING MENTIONED IN .XML FILE
    // IF USING DIFFERENT MODEL OF PROCESSOR


    // Reading any kind of data that is coming from device
    private void startReading(USBDataInterface usbDataInterface) {
        if (isReading) {
            Log.w("USB_READING", "Already reading data. Ignoring request.");
            return;
        }

        isReading = true;
        new Thread(() -> {
            final int BUFFER_SIZE = 64; // Increased buffer size
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;


            while (isReading && connection != null) {
                bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.length, 100); // Timeout 1 sec


                if (bytesRead > 0) {
                    // Process all received bytes
                    byte[] receivedData = Arrays.copyOf(buffer, bytesRead);
                    usbDataInterface.onDataReceived(receivedData, bytesRead);

                    // Log the received data in hex format
                    StringBuilder sb = new StringBuilder();
                    //SEND TO JS
                    sb.append("Received " + bytesRead + " bytes: ");
                    Log.d("USB_DATA INCOMING", "Sending to JS: " + sb.toString());
                    usbDataInterface.sendDataToJS(sb.toString());

                    for (byte b : receivedData) {
                        sb.append(String.format("%02X ", b));
                    }
                    Log.d("USB_DATA INCOMING", "Received " + bytesRead + " bytes: " + sb.toString());

                }  else {
                    Log.d("USB_DATA INCOMING", "No data received.");
                }
            }
            Log.d("USB_READING", "Stopped reading data.");
        }).start();
    }

    // Method to stop the reading thread
    public void stopReading() {
        isReading = false;
    }



    public void sendFloat(float floatValue) {
        new Thread(() -> {
            byte[] byteArray = floatToByteArray(floatValue);

            Log.d("BYTE ARRAY", " " + byteArray);


            // Log the byte array in hex format
            StringBuilder sb = new StringBuilder();
            sb.append("Sending float: " + floatValue + ", Byte array: ");
            for (byte b : byteArray) {
                sb.append(String.format("%02X ", b));
            }
            Log.d("BUFFER Converted", sb.toString());

            Log.d("Message2USB", "Sending float: " + floatValue + ", Byte array: " + sb.toString());


            if (outEndpoint != null && connection != null) {
                int bytesSent = connection.bulkTransfer(outEndpoint, byteArray, byteArray.length, 1000);
                if (bytesSent >= 0) {
                    Log.d("USB_SEND", "Sent " + bytesSent + " bytes to the board.");

                } else {
                    Log.e("USB_SEND", "Failed to send data to the board. Error code: " + bytesSent);
                }
            } else {
                Log.e("USB_SEND", "USB connection or outEndpoint is null. Cannot send data.");
            }
        }).start();
    }





}
