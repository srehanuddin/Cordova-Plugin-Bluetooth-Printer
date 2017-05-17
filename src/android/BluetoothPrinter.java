package com.ru.cordova.printer.bluetooth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.util.Xml.Encoding;
import android.util.Base64;

public class BluetoothPrinter extends CordovaPlugin {
	private static final String LOG_TAG = "BluetoothPrinter";
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mmSocket;
	BluetoothDevice mmDevice;
	OutputStream mmOutputStream;
	InputStream mmInputStream;
	Thread workerThread;
	byte[] readBuffer;
	int readBufferPosition;
	int counter;
	volatile boolean stopWorker;

	Bitmap bitmap;

	public BluetoothPrinter() {}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;
		} else if (action.equals("connect")) {
			String name = args.getString(0);
			if (findBT(callbackContext, name)) {
				try {
					connectBT(callbackContext);
				} catch (IOException e) {
					Log.e(LOG_TAG, e.getMessage());
					e.printStackTrace();
				}
			} else {
				callbackContext.error("Bluetooth Device Not Found: " + name);
			}
			return true;
		} else if (action.equals("disconnect")) {
            try {
                disconnectBT(callbackContext);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
    else if (action.equals("print") || action.equals("printImage")) {
			try {
				String msg = args.getString(0);
				printImage(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
		else if (action.equals("printText")) {
    			try {
    				String msg = args.getString(0);
    				printText(callbackContext, msg);
    			} catch (IOException e) {
    				Log.e(LOG_TAG, e.getMessage());
    				e.printStackTrace();
    			}
    			return true;
    		}
        else if (action.equals("printPOSCommand")) {
			try {
				String msg = args.getString(0);
                printPOSCommand(callbackContext, hexStringToBytes(msg));
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
		else if (action.equals("printQRCode")) {
        			try {
        				String msg = args.getString(0);
                        printQRCode(callbackContext, msg);
        			} catch (IOException e) {
        				Log.e(LOG_TAG, e.getMessage());
        				e.printStackTrace();
        			}
        			return true;
        		}
		return false;
	}

    //This will return the array list of paired bluetooth printers
	void listBT(CallbackContext callbackContext) {
		BluetoothAdapter mBluetoothAdapter = null;
		String errMsg = null;
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				errMsg = "No bluetooth adapter available";
				Log.e(LOG_TAG, errMsg);
				callbackContext.error(errMsg);
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
					/*
					Hashtable map = new Hashtable();
					map.put("type", device.getType());
					map.put("address", device.getAddress());
					map.put("name", device.getName());
					JSONObject jObj = new JSONObject(map);
					*/
					json.put(device.getName());
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("No Bluetooth Device Found");
			}
			//Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
	}

	// This will find a bluetooth printer device
	boolean findBT(CallbackContext callbackContext, String name) {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					if (device.getName().equalsIgnoreCase(name)) {
						mmDevice = device;
						return true;
					}
				}
			}
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Tries to open a connection to the bluetooth printer device
	boolean connectBT(CallbackContext callbackContext) throws IOException {
		try {
			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			beginListenForData();
			//Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// After opening a connection to bluetooth printer device,
	// we have to listen and check if a data were sent to be printed.
	void beginListenForData() {
		try {
			final Handler handler = new Handler();
			// This is the ASCII code for a newline character
			final byte delimiter = 10;
			stopWorker = false;
			readBufferPosition = 0;
			readBuffer = new byte[1024];
			workerThread = new Thread(new Runnable() {
				public void run() {
					while (!Thread.currentThread().isInterrupted() && !stopWorker) {
						try {
							int bytesAvailable = mmInputStream.available();
							if (bytesAvailable > 0) {
								byte[] packetBytes = new byte[bytesAvailable];
								mmInputStream.read(packetBytes);
								for (int i = 0; i < bytesAvailable; i++) {
									byte b = packetBytes[i];
									if (b == delimiter) {
										byte[] encodedBytes = new byte[readBufferPosition];
										System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
										/*
										final String data = new String(encodedBytes, "US-ASCII");
										readBufferPosition = 0;
										handler.post(new Runnable() {
											public void run() {
												myLabel.setText(data);
											}
										});
                                        */
									} else {
										readBuffer[readBufferPosition++] = b;
									}
								}
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});
			workerThread.start();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//This will send data to bluetooth printer
	boolean printText(CallbackContext callbackContext, String msg) throws IOException {
		try {

			mmOutputStream.write(msg.getBytes());

			// tell the user data were sent
			//Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent");
			return true;

		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	//This will send data to bluetooth printer
    boolean printImage(CallbackContext callbackContext, String msg) throws IOException {
        try {

            //final String encodedString = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAlgAAADSCAYAAACW0r5LAAAgAElEQVR4Xu2dB7QURfq3X8wZFbMiiogBcQ2omDDnBOa0JjDn7Io5R1wVEXPOYs6iuIoJBTNmMQurGDBgxO//1H41p+/c6emume7LhF+d41Hvra6ueqqZeah66+12f/9fMRUREAEREAEREAEREIHMCLSTYGXGUg2JgAiIgAiIgAiIgCMgwdKDIAIiIAIiIAIiIAIZE5BgZQxUzYmACIiACIiACIiABEvPgAiIgAiIgAiIgAhkTECClTFQNScC9Ubggw8+sEUXXbTQ7Xvvvdc233zz1MP47bffbIkllrAxY8a4a44//ng75ZRTWly///7726BBg0q2Offcc9tCCy1kPXv2tE022cTWWmstm2qqqVLfP67iBhtsYI899piNHTvWuAfl5ZdfthVWWMHoz8CBA6u6x59//mkvvPCC3XHHHTZp0iS7+OKLq2pPF4uACDQWAQlWY82nRiMCwQSKBWuLLbawe+65J3U7Dz74oG266aaF+qGCVXyjzTbbzK644oqCFKXuSKTi77//brPNNpvNN9989v777xd+c9NNN9nOO+9sgwcPtr333ruSpg1eSOjVV19to0ePdm3stddedtlll1XUni4SARFoTAISrMacV41KBFITKBYsLnz33Xeta9euqdrYdttt3SqOL+UEa8EFF7T//Oc/Ldr94Ycf7NNPP7XLL7/cHnjgAfe7tdde2x555BGbeuqpU/WhuBL9X3zxxW2XXXax6667rvDrY4891s4880x76qmnbI011ghq+6677rJrrrmm0MfoxRKsIJSqLAJNQUCC1RTTrEGKQDyBqGAhNH/88Yeddtpp1r9//0RsH330kS2yyCKunr+2nGAtvPDCxjWlCin5uO8JJ5zgfn333Xdb7969E/tQqsJ9991nrMSdf/75dthhhxWq+G3DL774wq1uhRSEDXFjnEjldtttZ+edd549/fTTWsEKAam6ItAkBCRYTTLRGqYIxBGIChbbZzfeeKOx0vTOO+/Y9NNPXxbc2Wefbcccc4yTrCWXXNLuv//+sjFY5QSLG/3888/WsWNH++6776qKk/L9euihh2yjjTZyY/DbhnPMMYd9/PHH1q5du6CHAqFaf/31XZzYPPPM467dcMMN7dFHH5VgBZFUZRFoDgISrOaYZ41SBGIJRAWLLTofT5UU7B4Nbj/33HNt+PDhLjap0hUs30FisOgH8vLwww9XNHM77rij3XLLLS5eyq+w+W1DfkcsVhZFgpUFRbUhAo1JQILVmPOqUYlAagJRwRo1apQNGDDArWL16dPHiDuKK6wOsZpDYUWIOCRO7VUrWGztscW3zjrr2NChQ1ONg1irG264IVXdaCXis7i20iLBqpScrhOBxicgwWr8OdYIRaAsgahgkXZgwoQJbiuMwgm8Ll26lLyeLbPbb7/dncpDbnr16mXPPPNMVYL1008/ue1JtggPOuggu/DCC1PN3hFHHOEC1ykTJ050p/s6dOjg0j/48vnnn9u4ceNsscUWs5lmmsn9mKD3LbfcMtU9SlWSYFWMTheKQMMTkGA1/BRrgCJQnkBUsAjYXnnllW3ppZe2t99+28444wz717/+1aoBcl517tzZ/ZxVq/XWW8969OhhI0eOrFiwCHJn9ev000937Q4bNszWXHPN4Ol7/PHHnSDSDgLlCyJF4Dx9j4pX8A0iF0iwqqGna0WgsQlIsBp7fjU6EUgkEBUs5GTddde1iy66yA4++GAnIojWdNNN16Kdc845x44++miXYPS1115zJ+v8KbukNA3PP/98i7aI5aIPV111ld12223ud4cccohdcMEFiX0vVaFUgDv3INno7LPPbh9++GFwgHtcRyRYFU2RLhKBpiAgwWqKadYgRSCeQFSwSBq68cYb25dffunkipQNnAyMJhJFVrp16+ZEhS08tvIonP5jG66aRKOIGqcSSdVQaTZ3stDTZ/oy//zzu74hgcsss4ztueeeLt9WVkWClRVJtSMCjUdAgtV4c6oRiUAQgahgDRkypBCT1LdvX5etfOutt26RSJSTfUgYMsR2m5eYWWaZxX788ceqBOvJJ590r8qptPz6668u9qpTp06FLOu0df3119uuu+7qMsT369ev0uZbXSfBygylGhKBhiMgwWq4KdWARCCMQFSw2KIjiSaFoHEvO9F0Bz64fY899nDber74vFKhpwhZLSOHFhndjzrqKGOLL6QceOCB7h2DFFbXXnnlFfeaHILZfWE1i3/Y0mzfvr378XHHHVc4BRlyv2hdCVal5HSdCDQ+AQlW48+xRigCZQlEBevWW291GcopvMyYwHW218466ywXc0U6BpKFUoqD0CsVLNrycVOsir3xxhst5Chp+kgnEfLuRN9edKxJ94j7vQSrUnK6TgQan4AEq/HnWCMUgYoEi4suvfRS22+//ZxUEexOzBWi1b17dyNnVjROqhrB+v77712brDIVvz8wZPqI3Tr11FNd/izyaFFI28CqFaceyU6fZZFgZUlTbYlAYxGQYDXWfGo0IhBMIG4Fi4bIG0WwO7FNxGexhUdw+6BBg2zfffdtca9qBIuGiI8iWSmFk4Y9e/YMHotPUvrJJ5+4fFqUt956y5Zaainbbbfd3MuasywSrCxpqi0RaCwCEqzGmk+NRgSCCZQTLBpDpAYPHuzSHCBcpGxgq5D/j5ZqBYuVppVWWsltESIuZIoPeV/gX3/95fo0xRRT2NixY92/KeS+IgcWGeoPPfTQYD7lLpBgZYpTjYlAQxGQYDXUdGowIhBOIEmweMfg6quvXmh4n332cVuHxaVawaI9Vsk4tUjhfYT+VTxpRuXjw3r37u2kyheSpfbv398Jm3/xc5r20tSRYKWhpDoi0JwEJFjNOe8atQgUCCQJ1qRJk9zKkj+px+twVltttVwEi8B6YqfIKL/88svbs88+a9NOO22q2fIZ3E866SQ78cQTC9dsv/32LoFpudf+pLpBiUoSrErJ6ToRaHwCEqzGn2ONUATKEkgSLC728VFIz4gRIwrbb9GGs1jBor1oeohrr73W5a9KUwYOHGikbIimmuD1O8RikQJi/PjxLndXlkWClSVNtSUCjUVAgtVY86nRiIAIiIAIiIAI1AABCVYNTIK6IAIiIAIiIAIi0FgEJFiNNZ8ajQiIgAiIgAiIQA0QkGDVwCSoCyIgAiIgAiIgAo1FQILVWPOp0YiACIiACIiACNQAAQlWDUyCuiACIiACIiACItBYBCRYjTWfGo0IiIAIiIAIiEANEJBg1cAkqAsiIAIiIAIiIAKNRUCC1VjzqdGIgAiIgAiIgAjUAAEJVg1MgrogAiIgAiIgAiLQWAQkWI01nxqNCIiACIiACIhADRCQYNXAJKgLIiACIiACIiACjUVAgtVY86nRiIAIiIAIiIAI1AABCVYNTIK6IAIiIAIiIAIi0FgEJFiNNZ8ajQiIgAiIgAiIQA0QkGDVwCSoCyIgAiIgAiIgAo1FQILVWPOp0YiACIiACIiACNQAAQlWDUyCuiACIiACIiACItBYBCRYjTWfGs1kJPDBBx/Yoosu6npw00032Y477lh1b/bbbz+79NJLbZFFFrHRo0fbNNNMk9jmwQcfbBdddJHNP//89vnnnyfWj1bw1/KzHj162IsvvmhTTDFF6jauueYa22OPPQr1x44da3PPPXeL69u1a+f+//jjj7dTTjmlxe9uvvlm22mnnUreb7bZZrMFFljAll9+eVtvvfVss802s5lnnjl136gYnaNbb73Vtttuu9jr3333XVtnnXXsiy++cPd8+OGHbc4553T1b7vtNtt+++3df7///vvWpUuXxH5su+22dscdd9jqq69uTz/9dGL9P/74w5Zaail77733bK+99rLLLrss8Zro+HbYYQf3HHrecRdfccUVrn3K119/bXPMMUfifVRBBEQgmYAEK5mRaohAKgJZC9a3335rHTt2tF9++cXd//HHH7d11103sS9ZCRY3evbZZ22VVVZJvCcV/v77b1t11VXt+eefL9TPUrCKO7Hkkkva9ddf7+QnbUkrWF9++aWTOKR2oYUWsieeeMI6d+5cuE1bCNawYcNs7bXXdvecbrrp7LPPPkuUn+j4uA6h23rrrcvikWClfXpUTwTCCEiwwniptgjEEshasG644QbbZZddCvfr27evXXnllYkzkKVg7b///jZw4MDEe1JhxIgRttJKK7WoW41gPfXUU9apU6dCexMnTnSrSQ888IBdeOGF7ufzzjuvvfDCC7bgggum6mMawfr+++/d6tjw4cONVTPkatlll23RflsI1j777NNi1YrVwd12263sOIsFixW/l19+udUqYrQRCVaqR0eVRCCYgAQrGJkuEIHSBLIWLFar+HJff/317bHHHnOrGJ988onNNddcZacgC8GaeuqpjS2qGWaYwd0zzbbRoYceav/+97/NX0snqxGscltvjz76qG244YaOw1FHHWVnn312qscySbCQOLZ277nnHjeOBx980K1kFZe8Beubb75xcsnqpZ//Xr162X/+858gwaJy0vaiBCvVo6NKIhBMQIIVjEwXiED+gvXWW2+5+BvK66+/7mKF3n77bbvqqqtaxDiV6kkWgkW8EFuS3333nfEF3K9fv7LTznYmW2k//vij7brrrnbddde5+nkJFm1vs802duedd7r7fvTRR4mxRlxTTrD+/PNPY8Xu8ssvd32/8cYbY+PB8hYs+LFatfDCC7sVu27durk+vfbaa7b00kvHzkV0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8Ouvv9pDDz3k/nnxxRfts88+s3nmmce6du1qG220kW2++eY2//zzB43/jz/+sH/84x/29ttv23XXXWe77LJL0PXVVM56PO+++64tvvjirktpx3LzzTfbTjvtVHIYs802my2wwAK2/PLL23rrrWebbbaZzTzzzCXrdu7c2caMGdPqd1zfvXt3W3PNNW2LLbawxRZbLBhZJeOKXlN8wxlmmMHmm28+W3rppW2NNdawrbbaKvi5oU0/5oMOOsguvPDC2HH9+OOP1qdPH3viiSccP/69wgoruPq33Xabbb/99u6/33//fevSpUsin2233dbuuOMOW3311e3pp59OrE+F/fbbzy699FJbZJFFbPTo0TbNNNMkXufHR59GjBhhPA/lyquvvmrLLrusq/Loo4/a+uuvn3gPVRCBeiIgwaqn2VJfUxN45513bPfdd7cXXngh9hq+AM4//3xXL23hi2DDDTd0Xx4fffSRzTrrrGkvrapeHuM566yz7F//+pfr11prrWVPPvlkYh/LCVbxxUsuuaRdf/31TriKS5xgFdc77rjjjH+mnXbaxL75CpWMq5xgFd+4ffv2NmjQINtxxx1T94mKaQTrt99+s912281uvfVW1zZ/OeAvA760hWB9++231rFjR/vll1/cbR9//HFbd911E8candNDDz3UBgwYUPYaCVYiUlWocwISrDqfQHW/NYEvv/zSVl55Zfv000+dCPFhv/baa9vcc89tEyZMcGKEWHn5uvLKK61v376pULJ6wJfcMcccY2eeeWaqa6qtlMd4fv/9d0OAPvzww0L33njjDVtqqaXKdjcqWE899ZR16tSpUH/ixIn2xRdf2AMPPFBYoZl33nkd5wUXXLBFu/7LGJk48cQTC79jfljZGjJkiN1www3u55tuuqndeOONhtgklUrHFRWsiy++2N3Tlz///NO++uore+mll+yMM86w8ePHl5SfpL4lCdakSZPcs3rRRRe5pq655honW9HSFoIF9+jKLH82+DOSVIqlmeeDFb+4IsFKIqrf1zsBCVa9z6D634rAUUcdZeeee65NN910bmuQrZ3iwlYfcVSXXXaZq/fBBx8kbvsgI35L5vXXX3dbWW1R8hgP206sSrD9tdJKK9mwYcPs5JNPthNOOKHskKKCVW6Lyq/00Rj9P/vss1u0myQbVH744Ydtu+22M7bM0qyI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BwlW9Z9XakEE6oGABKseZqkN+ijBkmCFykeoMIXWz0NuQseYRx8kWG3wgaZbiEANEJBg1cAk1EIXJFgSrFD5CBWm0Pp5yE3oGPPogwSrFj7x1AcRyJ+ABCt/xrqDCIiACIiACIhAkxGQYDXZhGu4IiACIiACIiAC+ROQYOXPWHcQAREQAREQARFoMgISrCabcA1XBERABERABEQgfwISrPwZ6w4iIAIiIAIiIAJNRkCC1WQTruG2JjBixAjbcccd7cMPP7STTz7ZTjjhhIow0c5OO+1kH3zwgZ100kl24oknlm3nnnvusS233NKiJ9vS3PiRRx6xDTbYoGTVv/76yx5++GG7++677dlnn7Uvv/zS5ptvPltppZWsT58+tummm9pUU02VeJus2pk4caLdeeeddv/999uoUaNswoQJtthii9mGG25oO++8s3Xq1CmxL1QYN26c3XvvvW5so0ePtvHjx1vnzp1trbXWcsyXXnrpVO2okgiIgAi0FQEJVluR1n1qksD1119v++yzjyEClEoF64YbbrC999670M7kEKzvvvvO9thjDycipaSNlAMbb7yxXXfdddahQ4fY+ciqnY8//th22GEHe/HFF1v1h77MNttsds0119jmm28e2xdEb/DgwU5Ykaq4cf373/+2gw46qCafMXVKBESgOQlIsJpz3pt+1L/++qsdf/zxdt555zkWM8wwg/3yyy/BgkU7rHide+65LdpJI1hvv/22PfHEE6nm4qWXXjJkkPL6669b9+7dW1yHeGy//fZ2++23u59vtNFGboVorrnmsrFjxxoC+Nhjj7nfbb311q5eqRxPtLPddtvZHXfcUVU7P/zwg6277rr28ssvu3b69evnVt1mnHFGe//99+2SSy6x9957z6aeemobOnSo9erVqySHQw45xC688EL3uwUWWMB23XVXW3bZZd0q3KuvvmrnnHOOmzcKq3a9e/dOxVOVREAERCBvAhKsvAmr/Zoj8NVXX1nfvn3ddhPl2muvtUsvvdSttISsYNEO4vDQQw+5dliNueyyy+yFF15ItUUYAoYtzFtuucVJCv0uliO233r06OFWePbaay8bNGiQTTnllIVbTJo0yf38qquucj8bOXKkLbfccq26QDvLL7+8+zn14UISWl/StsOK0qGHHuouu+mmm9wWbLSwGoXoPfXUU/aPf/zDnn/+eZt++ulb9ee1115z24AHHnigHXbYYda+ffsWdehvz5497Y8//nDSSf245KAhvFVXBERABKolIMGqlqCurysCrDituOKK9sYbb7hVqxtvvNHFJhHDw8/SChbtENfEahLtsEJEPBWywM/SrGClBUdMV9euXZ08sbKEmBQXtv123313V4eVHfpRXOiX/zn1d9lll5Lt7Lbbbu7nyEqp2KY07XTr1s3FStFXvxpWfDNWsqiHHA0ZMsTxK1V8HFkcr6OOOqqwgkibXbp0SYtW9URABEQgNwISrNzQquFaJcD22NFHH2233Xabky3Koosu6oLT0woW10TbWWGFFdzKCSLEl3yWgkWfaG/++ee3d999122zlRMsAsLZGiwu/HyeeeZxP04jWP/9739tzjnnDG6HlT0C68vdxzeKFLKCSKA6sltJYYWM7VDKc889ZyuvvHIlzegaERABEciUgAQrU5xqrF4IIA9RCSG+54svvggSLMZa3E7Hjh3t888/z0ywOHWH/HGfU0891fr3719yC+zpp5+2Nddcs+wKFvFQiCDlmWeesdVWW63VdNHOGmus4X4et4KV1A4rV6xMUYj7Wm+99WIfiyuuuMJtRc4777yOfyXbe6we+tW4uD7Xy3OpfoqACDQOAQlW48ylRlIFAWTr66+/Dhas4lvOPffcToayWsFCHgjsZuuPoHBkq1Rhm40YLLbvjjzySDv77LNbyArXEy929dVXuxUeRKpUugbaIQaL7VLaIYg8WtK0wyrb4osv7i5LCjwnrszHZ7Hy5VfYQqbytNNOcwcWKN98803ZE5Ih7aquCIiACFRDQIJVDT1d2zAE5phjDpcGIGSLsNTg2VLjSz4LwSKgnFUmAsC33XZbu/XWW8uu8BAwThD877//bgMGDHCB4UjUn3/+aUgIY0MAH3300ZIxWn48tLP++uu72KgLLrjADjjggKB2SPMw++yzu+YQPWKk4goS509yVhI/RR+XWWYZF++1zTbbFE5RNsyDqYGIgAjULQEJVt1OnTqeJYFaFKzhw4e79AWsGnFykOScSYUVI3JhkSaB03d77rmnsQ335JNPOlkbOHBgWbny7d91112uHbYo1157bbf6FdIOfUXk2Hp95ZVXDL7FhW3KddZZx4kcpVT6iaTx0s+tttrKVSOZKYlUVURABESgFghIsGphFtSHyU6gFgULwSH1A5nPiS2adtppU3EiXxYnAREzYpr490ILLeTioeK2GEs1TCC8P1EY2g7Z5snFRUGiWNEjfxVjIEYNMSJ/GIcCEDBK6AoW7SCNn3zyiUtWilxGU0qkgqVKIiACIpATAQlWTmDVbH0RqDXBQhoWXnhhJ0fklDr44IMTgZI6gq3A008/3Z3+49/IGT97/PHHXeb0iy66qHDiLq5B2iGg/owzzqiqHe5/3HHHxfabrUded0N+KwpB7v70YdJgybxPWgdEjgB5co8tuOCCSZfp9yIgAiLQZgQkWG2GWjeqZQK1JlhnnXWWHXvssS72acyYMS5FQ7nCNhspD0hZsMQSS9h9991XyAdFDBapEIjJ8vIUJz60w6rVzTff7Nph222RRRZxtw5px/eVbUKkzidj5ee0S4Z2kr0SF4bMUdiOnHnmmRMfE2LTkDOSoFLIhs82pooIiIAI1BIBCVYtzYb6MtkI1JJg8eoXTuF99tlnLoaK7PBJ6Qsuv/xy905FhIwtN58mIQqUOC7eRUiJO91HO7xTkVfYkLB0ySWXbDUnadopvuinn36yb7/91qabbjoXj+W38ni9D/nIyMJODFaaEj01CBvSPKiIgAiIQK0RkGDV2oyoP5OFQC0JFpnPeR8g24OkU1h99dXLMmFFBxEiPcJ+++3nAtnjhMwn9uQ1OeSzitajHVaXSAex//77u3biSrl20k4g9+vUqZOLySLezL/Gp9z1BNojgLAhNcMpp5yS9naqJwIiIAJtSkCC1aa4dbNaJVArgoU48JJkTv2R14rYoug7BUvxY6ULUeFaguJ9YHqpugTAk1eLwnWc8vOF//dxTGwp+nqh7aSd42jC0rjM8tG2oicGEUm2HpPYpO2L6omACIhA1gQkWFkTVXt1SaBWBGvEiBHu5cXI0pVXXunilJJK9F2FJCb1r40pdR25tHbYYQf3qw8//NAFmftCO/6UIa+t4fU1caVcO0n99b8//PDDXb4uCu8bJFg9rpCbi1OJxJDRL1a70p6qTNsf1RMBERCBLAlIsLKkqbbqlkCtCBbB25dccokL9ia4vUOHDolMSexJPaSMwHhilOK2CDmNyMoPsVBkrp9pppkK7UcThNIOpwDjSrl2Ejv8fxUQSV6WTSH4nj7FlVGjRrlVPfpHOgayv/OCbRUREAERqGUCEqxanh31rc0I1IJg8aoYTuyRgoDUBWQ4Twpu94A22WQTd1IP8WBbkaDx4sK2IwlAOSnIyhgrZMUl2s6LL75oSy21VEXtlJs4EozyehxirxBDgtvj0jMQD0ZWedJWkDh1yJAhLt2EigiIgAjUOgEJVq3PkPrXJgRqQbDId4VYsRI1cuRIIxA9bXnuuedc0k2uZfWLdrieV9YgbsOGDSukNeB1OWSJ79KlS6vmaWfVVVd1P6+mHa4n9xYyh0SxtUeM14MPPuhe+ePbRwpLvXSa33///fcuwP/NN9909VnpisaMxbHh5KUkLO2To3oiIAJ5EZBg5UVW7dYVgcktWL/99ptbLSIOipxOQ4cOTb165UGTeoGUBbwLMa5w2pBAd17oHFdoB0nh3YzVtEOmdp/jqrgdpIr3HBLIH1eiMWEhD9Onn35qHTt2DLlEdUVABEQgcwISrMyRqsF6JDC5BYvEoL1793YrUMQYkR+qkkKw+J133ulOIbL1Nm7cOLf9hlARJN6nTx+bZZZZEpvOoh1ONLLNSYb29u3bu9f10A9eSM123zTTTFO2HxKsxGlSBREQgRomIMGq4clR19qOAGJDSRvzFNezatqp5tri/vi2oj+vZGzVtpPH9UlPRSXjTGpTvxcBERCBUAISrFBiqi8CIiACIiACIiACCQQkWHpEREAEREAEREAERCBjAhKsjIGqOREQAREQAREQARGQYOkZEAEREAEREAEREIGMCUiwMgaq5kRABERABERABERAgqVnQAREQAREQAREQAQyJiDByhiomhMBERABERABERABCZaeAREQAREQAREQARHImIAEK2Ogak4EREAEREAEREAEJFh6BkRABERABERABEQgYwISrIyBqjkREAEREAEREAERkGDpGRABERABERABERCBjAlIsDIGquZEQAREQAREQAREQIKlZ0AEREAEREAEREAEMiYgwcoYqJoTAREQAREQAREQAQmWngEREAEREAEREAERyJiABCtjoGpOBERABERABERABP4fcWceiKHu6IcAAAAASUVORK5CYII=";
            final String encodedString = msg;
            final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",")  + 1);

            final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            bitmap = decodedBitmap;

            int mWidth = bitmap.getWidth();
            int mHeight = bitmap.getHeight();
            //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
            bitmap=resizeImage(bitmap, 48 * 8, mHeight);


            byte[]  bt =getBitmapData(bitmap);

            bitmap.recycle();

            mmOutputStream.write(bt);

            // tell the user data were sent
            //Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;


        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }


    boolean printPOSCommand(CallbackContext callbackContext, byte[] buffer) throws IOException {
        try {
            //mmOutputStream.write(("Inam").getBytes());
            //mmOutputStream.write((((char)0x0A) + "10 Rehan").getBytes());
            mmOutputStream.write(buffer);
            //mmOutputStream.write(0x0A);

            // tell the user data were sent
			Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    boolean printQRCode(CallbackContext callbackContext, String str) throws IOException {
            try {
            	int nVersion = 0;
            	int nErrorCorrectionLevel = 3;
            	int nMagnification = 8;

                 byte[] bCodeData = null;
                 try
                 {
               	  bCodeData = str.getBytes("UTF-8");

                 }
                 catch (UnsupportedEncodingException e)
                 {
                 	String errMsg = e.getMessage();
					Log.e(LOG_TAG, errMsg);
				   	e.printStackTrace();
				   	callbackContext.error(errMsg);
                 }

                 byte[] command = new byte[bCodeData.length + 7];

                 command[0] = (byte) 27;
                 command[1] = (byte) 90;
                 command[2] = ((byte)nVersion);
                 command[3] = ((byte)nErrorCorrectionLevel);
                 command[4] = ((byte)nMagnification);
                 command[5] = (byte)(bCodeData.length & 0xff);
                 command[6] = (byte)((bCodeData.length & 0xff00) >> 8);
                 System.arraycopy(bCodeData, 0, command, 7, bCodeData.length);

                //mmOutputStream.write(("Inam").getBytes());
                //mmOutputStream.write((((char)0x0A) + "10 Rehan").getBytes());
                mmOutputStream.write(command);
                //mmOutputStream.write(0x0A);

                // tell the user data were sent
    			Log.d(LOG_TAG, "Data Sent");
                callbackContext.success(command);
                return true;
            } catch (Exception e) {
                String errMsg = e.getMessage();
                Log.e(LOG_TAG, errMsg);
                e.printStackTrace();
                callbackContext.error(errMsg);
            }
            return false;
        }

	// disconnect bluetooth printer.
	boolean disconnectBT(CallbackContext callbackContext) throws IOException {
		try {
			stopWorker = true;
			mmOutputStream.close();
			mmInputStream.close();
			mmSocket.close();
			callbackContext.success("Bluetooth Disconnect");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


	public byte[] getText(String textStr) {
        // TODO Auto-generated method stubbyte[] send;
        byte[] send=null;
        try {
            send = textStr.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            send = textStr.getBytes();
        }
        return send;
    }

    public static byte[] hexStringToBytes(String hexString) {
        hexString = hexString.toLowerCase();
        String[] hexStrings = hexString.split(" ");
        byte[] bytes = new byte[hexStrings.length];
        for (int i = 0; i < hexStrings.length; i++) {
            char[] hexChars = hexStrings[i].toCharArray();
            bytes[i] = (byte) (charToByte(hexChars[0]) << 4 | charToByte(hexChars[1]));
        }
        return bytes;
    }

    private static byte charToByte(char c) {
		return (byte) "0123456789abcdef".indexOf(c);
	}

















	public byte[] getImage(Bitmap bitmap) {
        // TODO Auto-generated method stub
        int mWidth = bitmap.getWidth();
        int mHeight = bitmap.getHeight();
        bitmap=resizeImage(bitmap, 48 * 8, mHeight);
        //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
        /*
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        int[] mIntArray = new int[mWidth * mHeight];
        bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
        byte[]  bt =getBitmapData(mIntArray, mWidth, mHeight);*/

        byte[]  bt =getBitmapData(bitmap);


        /*try {//?????????????????
            createFile("/sdcard/demo.txt",bt);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/


        ////byte[]  bt =StartBmpToPrintCode(bitmap);

        bitmap.recycle();
        return bt;
    }

    private static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        Bitmap BitmapOrg = bitmap;
        int width = BitmapOrg.getWidth();
        int height = BitmapOrg.getHeight();

        if(width>w)
        {
            float scaleWidth = ((float) w) / width;
            float scaleHeight = ((float) h) / height+24;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleWidth);
            Bitmap resizedBitmap = Bitmap.createBitmap(BitmapOrg, 0, 0, width,
                    height, matrix, true);
            return resizedBitmap;
        }else{
            Bitmap resizedBitmap = Bitmap.createBitmap(w, height+24, Config.RGB_565);
            Canvas canvas = new Canvas(resizedBitmap);
            Paint paint = new Paint();
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, (w-width)/2, 0, paint);
            return resizedBitmap;
        }
    }

    public static byte[] getBitmapData(Bitmap bitmap) {
		byte temp = 0;
		int j = 7;
		int start = 0;
		if (bitmap != null) {
			int mWidth = bitmap.getWidth();
			int mHeight = bitmap.getHeight();

			int[] mIntArray = new int[mWidth * mHeight];
			bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
			bitmap.recycle();
			byte []data=encodeYUV420SP(mIntArray, mWidth, mHeight);
			byte[] result = new byte[mWidth * mHeight / 8];
			for (int i = 0; i < mWidth * mHeight; i++) {
				temp = (byte) ((byte) (data[i] << j) + temp);
				j--;
				if (j < 0) {
					j = 7;
				}
				if (i % 8 == 7) {
					result[start++] = temp;
					temp = 0;
				}
			}
			if (j != 7) {
				result[start++] = temp;
			}

			int aHeight = 24 - mHeight % 24;
			int perline = mWidth / 8;
			byte[] add = new byte[aHeight * perline];
			byte[] nresult = new byte[mWidth * mHeight / 8 + aHeight * perline];
			System.arraycopy(result, 0, nresult, 0, result.length);
			System.arraycopy(add, 0, nresult, result.length, add.length);

			byte[] byteContent = new byte[(mWidth / 8 + 4)
					* (mHeight + aHeight)];//
			byte[] bytehead = new byte[4];//
			bytehead[0] = (byte) 0x1f;
			bytehead[1] = (byte) 0x10;
			bytehead[2] = (byte) (mWidth / 8);
			bytehead[3] = (byte) 0x00;
			for (int index = 0; index < mHeight + aHeight; index++) {
				System.arraycopy(bytehead, 0, byteContent, index
						* (perline + 4), 4);
				System.arraycopy(nresult, index * perline, byteContent, index
						* (perline + 4) + 4, perline);
			}
			return byteContent;
		}
		return null;

	}

	public static byte[] encodeYUV420SP(int[] rgba, int width, int height) {
		final int frameSize = width * height;
		byte[] yuv420sp=new byte[frameSize];
		int[] U, V;
		U = new int[frameSize];
		V = new int[frameSize];
		final int uvwidth = width / 2;
		int r, g, b, y, u, v;
		int bits = 8;
		int index = 0;
		int f = 0;
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				r = (rgba[index] & 0xff000000) >> 24;
				g = (rgba[index] & 0xff0000) >> 16;
				b = (rgba[index] & 0xff00) >> 8;
				// rgb to yuv
				y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
				u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
				v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
				// clip y
				// yuv420sp[index++] = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 :
				// y));
				byte temp = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 : y));
				yuv420sp[index++] = temp > 0 ? (byte) 1 : (byte) 0;

				// {
				// if (f == 0) {
				// yuv420sp[index++] = 0;
				// f = 1;
				// } else {
				// yuv420sp[index++] = 1;
				// f = 0;
				// }

				// }

			}

		}
		f = 0;
		return yuv420sp;
	}


}
