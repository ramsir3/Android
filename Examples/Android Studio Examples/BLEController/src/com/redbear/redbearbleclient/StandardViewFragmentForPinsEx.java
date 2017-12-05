package com.redbear.redbearbleclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Fragment;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;
import com.redbear.RedBearService.IRedBearServiceEventListener;
import com.redbear.RedBearService.RedBearService;
import com.redbear.protocol.IRBLProtocol;
import com.redbear.protocol.RBLProtocol;
import com.redbear.redbearbleclient.MainPage.Device;
import com.redbear.redbearbleclient.data.PinInfo;

public class StandardViewFragmentForPinsEx extends Fragment implements
		IRBLProtocol {

	RollingWindow ecgroll = new RollingWindow(13);

	final String TAG = "StdViewFragmentForPins";
	final long timeout = 3000;
	public static final int RST_CODE = 10;
	Device mDevice;
	TextView textRssi;
	TextView textName;
	ProgressBar mLoading;
	LinearLayout pins_list;
	boolean isFirstReadRssi = true;
	boolean isFirstReadPin = true;
	RedBearService mRedBearService;
	RBLProtocol mProtocol;
	SparseArray<PinInfo> pins;
	HashMap<String, PinInfo> changeValues; // to init value
	HashMap<String, View> list_pins_views = null;
	PinAdapter mAdapter;
    Timer mTimer = new Timer();
    Timer mDataTimer = new Timer();
    TimerTask mTimerTask;
    TimerTask mDataTimerTask;
	boolean timerFlag;

    private Runnable mTimer2;

    LineGraphSeries<DataPoint> accelerometer_series;
    LineGraphSeries<DataPoint> ecg_series;
	LineGraphSeries<DataPoint> hrv_series;

    Button zeroAcc;
    Button HRFatButton;
    Button RPEFatButton;
    Button HRVFatButton;
    Button CIFatButton;
    Button ForceFatButton;
    int fatcounter = 0;

	TextView textAccelerometer;
	TextView textECG;
	TextView textForce;
	TextView textCI;
	TextView textHRV;
	TextView textRPE;
	TextView textFatIndCount;

    int zeroX = 512;
    int zeroY = 512;
    int zeroZ = 512;
    final int windowSize = 10;
    final int timeStep = 10; //ms
    final int maxPoints = 100 + (int)(windowSize*1000/timeStep);
    final int pinXnumber = 18;
    final int pinYnumber = 19;
    final int pinZnumber = 20;
    final int pinHnumber = 21;
    PinInfo pinX;
    PinInfo pinY;
    PinInfo pinZ;
    PinInfo pinH;
    double time = 0;
	double CI = 0;
	double RPE = 0;

	final double mass = 0.58; //average weight of fist
	final double SV = 0.07; //assuming average healthy 70kg man, which is 70mL
	final double height = 1.77; //m of average male https://www.cdc.gov/nchs/fastats/body-measurements.htm
	final double waistcirc = 1.015; //m
	final double waistr = waistcirc/(2*Math.PI);
	final double SA = (2*waistr*Math.PI)*(height + waistr);


	double[] rate = new double[10];                    // array to hold last ten IBI values
	double sampleCounter = 0;          // used to determine pulse timing
	double lastBeatTime = 0;           // used to find IBI
	double P = 512;                      // used to find peak in pulse wave, seeded
	double T = 512;                     // used to find trough in pulse wave, seeded
	double thresh = 512;                // used to find instant moment of heart beat, seeded
	double amp = 0;                   // used to hold amplitude of pulse waveform, seeded
	boolean firstBeat = true;        // used to seed rate array so we startup with reasonable BPM
	boolean secondBeat = false;      // used to seed rate array so we startup with reasonable BPM
	double BPM = 0;
	double HRV = 0;

	RollingWindow bpmrtrw = new RollingWindow(10);
	double runningTotal = 0;

	RollingWindow hrvrw = new RollingWindow(10);

	//added variables?
	double IBI = 0; //just has to make it so that IBI/5*3 is smaller than N with N being 2
	boolean Pulse = false;


	public StandardViewFragmentForPinsEx() {
	}

	public StandardViewFragmentForPinsEx(Device mDevice, RedBearService mRedBearService) {
		this.mDevice = mDevice;
		pins = new SparseArray<PinInfo>();
		changeValues = new HashMap<String, PinInfo>();
		list_pins_views = new HashMap<String, View>();
		this.mRedBearService = mRedBearService;

        ecg_series = new LineGraphSeries<DataPoint>();
		hrv_series = new LineGraphSeries<DataPoint>();
        accelerometer_series = new LineGraphSeries<DataPoint>();
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.activity_standardview_pins_ex,
				null);

		textRssi = (TextView) view.findViewById(R.id.text_rssi);
		textName = (TextView) view.findViewById(R.id.text_devicename);
		pins_list = (LinearLayout) view.findViewById(R.id.pins_list);
		pins_list.setEnabled(false);

		pins_list.setVisibility(View.GONE);

        textAccelerometer = (TextView) view.findViewById(R.id.Accelerometer);
        textECG = (TextView) view.findViewById(R.id.ECG);
		textForce = (TextView) view.findViewById(R.id.Force);
		textCI = (TextView) view.findViewById(R.id.CI);
		textHRV = (TextView) view.findViewById(R.id.HRV);
		textRPE = (TextView) view.findViewById(R.id.RPE);
		textFatIndCount = (TextView) view.findViewById(R.id.FatIndCount);

        zeroAcc = (Button) view.findViewById(R.id.zeroAcc);
		HRFatButton = (Button) view.findViewById(R.id.HRFatButton);
		RPEFatButton = (Button) view.findViewById(R.id.RPEFatButton);
		HRVFatButton = (Button) view.findViewById(R.id.HRVFatButton);
		CIFatButton = (Button) view.findViewById(R.id.CIFatButton);
		ForceFatButton = (Button) view.findViewById(R.id.ForceFatButton);

//	to link the xml with this file	zeroAcc = (Button) view.findViewById(R.id.zeroAcc);
//	to set colour of the button - changed, because the original can just set on xml file	zeroAcc.setBackgroundColor();

		GraphView ecg_graph = (GraphView) view.findViewById(R.id.ecg_graph);
		ecg_graph.setTitle("Raw Pulse Signal");
        ecg_series = new LineGraphSeries<DataPoint>();
        ecg_graph.addSeries(ecg_series);
        ecg_graph.setEnabled(true);
        Viewport viewportE = ecg_graph.getViewport();
        viewportE.setXAxisBoundsManual(true);
        viewportE.setMinX(0);
        viewportE.setMaxX(windowSize);
        viewportE.setScrollable(true);
		GridLabelRenderer ecg_label = ecg_graph.getGridLabelRenderer();
		ecg_label.setPadding(65);

		GraphView hrv_graph = (GraphView) view.findViewById(R.id.hrv_graph);
		hrv_graph.setTitle("HRV Graph");
		hrv_series = new LineGraphSeries<DataPoint>();
		hrv_graph.addSeries(hrv_series);
		hrv_graph.setEnabled(true);
		Viewport viewportH = hrv_graph.getViewport();
		viewportH.setXAxisBoundsManual(true);
		viewportH.setMinX(0);
		viewportH.setMaxX(windowSize);
		viewportH.setScrollable(true);
		GridLabelRenderer hrv_label = hrv_graph.getGridLabelRenderer();
		hrv_label.setPadding(65);

		GraphView accelerometer_graph = (GraphView) view.findViewById(R.id.accelerometer_graph);
		accelerometer_graph.setTitle("Accelerometer Graph");
		accelerometer_series = new LineGraphSeries<DataPoint>();
		accelerometer_graph.addSeries(accelerometer_series);
		accelerometer_graph.setEnabled(true);
		Viewport viewportA = accelerometer_graph.getViewport();
		viewportA.setXAxisBoundsManual(true);
		viewportA.setYAxisBoundsManual(true);
		viewportA.setMinX(0);
		viewportA.setMaxX(windowSize);
		viewportA.setMinY(0);
		viewportA.setMaxY(10);
		viewportA.setScrollable(true);
		GridLabelRenderer acc_label = accelerometer_graph.getGridLabelRenderer();
		acc_label.setPadding(50);

		mLoading = (ProgressBar) view.findViewById(R.id.pin_loading);
		if (mDevice != null) {
			textName.setText(mDevice.name);

			mDevice.rssi = 0;

			textRssi.setText("Rssi : " + mDevice.rssi);

			mProtocol = new RBLProtocol(mDevice.address);
			mProtocol.setIRBLProtocol(this);
		}

		zeroAcc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if ( pinX != null && pinY != null && pinZ != null) {
                    zeroX = pinX.value;
                    zeroY = pinY.value;
                    zeroZ = pinZ.value;
                }
            }
        });

        mDataTimerTask = new TimerTask() {

            @Override
            public void run() {
//                Log.d(TAG, "HELLO: " + pins.size());
                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pins != null && pins.size() > 16) {
//                    pinX != null && pinY != null && pinZ != null

                            int p = 0;
                            while (p < pins.size() && (pinX == null || pinY == null || pinZ == null || pinH == null)) {
                                Log.i(TAG, "Looping at: "+p  );
                                PinInfo curP = pins.valueAt(p);
                                if (curP.pin == pinXnumber) {
                                    pinX = curP;
                                    Log.i(TAG, "Found: "+pinX.pin  );
                                }
                                if (curP.pin == pinYnumber) {
                                    pinY = curP;
                                    Log.i(TAG, "Found: "+pinY.pin  );
                                }
                                if (curP.pin == pinZnumber) {
                                    pinZ = curP;
                                    Log.i(TAG, "Found: "+pinZ.pin  );
                                }
                                if (curP.pin == pinHnumber) {
                                    pinH = curP;
                                    Log.i(TAG, "Found: "+pinH.pin  );
                                }
                                p++;
                            }
                            //if (pinH != null) {
                            if (pinX != null && pinY != null && pinZ != null && pinH != null) {
//                                Log.i(TAG, String.format("x: %d\ny: %d\nz: %d\n", pinX.value, pinY.value, pinZ.value));
                                double am = updateAccelerometerSeries(pinX, pinY, pinZ, time, timeStep);
//                                Log.i(TAG, "Acc Series: " + time + ", " + accelerometer_series.getHighestValueX());
//                                Log.i(TAG, String.format("Raw Pulse Signal: %d", pinH.value));
                                double[] em = updateECGSeries(pinH, time++, timeStep);
                                Log.i(TAG, "ECG Series: " + time + ", " + ecg_series.getHighestValueX());
//								Log.i(TAG, String.format("HRV: %f", em[2]));
//								Log.i(TAG, "HRV Series: " + time + ", " + hrv_series.getHighestValueX());

                                updateAccelText(am);
                                updateECGText(em[1]);		//double[] outs = {mag, BPM, HRV, CI, RPE};
								updateOtherMetricsText(am*mass, em[3], em[2], em[4]);
								updateFatigueIndicators(em[1], em[4], em[2], em[3], am*mass);
								//anything that needs to happen every 10 ms needs to go here
								//updateFatigueIndicators
                            }
//            mHandler.postDelayed(this, 200);
                        }
                    }
                });

            }
        };

		mDataTimer.schedule(mDataTimerTask, 0, timeStep);

		timerFlag = false;
		mTimerTask = new TimerTask() {

			@Override
			public void run() {
				if (getActivity() != null) {
					getActivity().setResult(RST_CODE);
					getActivity().finish();
					getActivity().runOnUiThread(new Runnable() {

						@Override
						public void run() {
							new AlertDialog.Builder(MainPage.instance)
									.setTitle("Error")
									.setMessage(
											"No response from the BLE Controller sketch.")
									.setPositiveButton("OK", null).show();
						}
					});
				}
			}
		};



		return view;
	}

	@Override
	public void onDestroy() {
		mHandler.removeMessages(0);
		super.onDestroy();
	}

	@Override
	public void onResume() {
		if (mRedBearService != null) {
			if (mDevice != null) {
				if (mProtocol != null) {
					mProtocol.setmIRedBearService(mRedBearService);
				}
				mRedBearService.setListener(mIRedBearServiceEventListener);
				textName.post(new Runnable() {

					@Override
					public void run() {
						mRedBearService.readRssi(mDevice.address);
					}
				});
			}
		}
		
//		new Timer().schedule(new TimerTask() {
//			
//			@Override
//			public void run() {
				if (textRssi != null) {
					textRssi.postDelayed(new Runnable() {

						@Override
						public void run() {
							if (mProtocol != null) {

								// 1. queryProtocolVersion
								// 2. queryTotalPinCount
								// 3. queryPinAll
								// 4. end with "ABC"
								mProtocol.queryProtocolVersion();
							}
							mHandler.sendEmptyMessageDelayed(1, timeout);
						}
					}, 300);
				}
//			}
//		}, 1000);

//        mTimer2 = new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        };
//        mHandler.postDelayed(mTimer2, 1000);

		super.onResume();
	}

	final IRedBearServiceEventListener mIRedBearServiceEventListener = new IRedBearServiceEventListener() {

		@Override
		public void onDeviceFound(String deviceAddress, String name, int rssi,
				int bondState, byte[] scanRecord, ParcelUuid[] uuids) {

			// to do nothing
		}

		@Override
		public void onDeviceRssiUpdate(final String deviceAddress,
				final int rssi, final int state) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {

					deviceRssiStateChange(deviceAddress, rssi, state);
					if (isFirstReadRssi) {
						mHandler.sendEmptyMessageDelayed(0, 1000);
						isFirstReadRssi = false;
					} else {
						mHandler.sendEmptyMessageDelayed(0, 300);
					}
				}
			});
		}

		@Override
		public void onDeviceConnectStateChange(final String deviceAddress,
				final int state) {

			if (getActivity() != null) {
				getActivity().runOnUiThread(new Runnable() {

					@Override
					public void run() {
						deviceConnectStateChange(deviceAddress, state);
					}
				});
			}
		}

		@Override
		public void onDeviceReadValue(int[] value) {
			// Log.e(TAG, "value : " + value);

			if (mProtocol != null) {
				mProtocol.parseData(value);
			}

		}

		@Override
		public void onDeviceCharacteristicFound() {
			// TODO Auto-generated method stub
			
		}
	};

	Handler.Callback mHandlerCallback = new Handler.Callback() {
		@Override
		public boolean handleMessage(Message msg) {

			if (msg.what == 0) {
				if (mRedBearService != null) {
					if (mDevice != null) {
						mRedBearService.readRssi(mDevice.address);
					}
				}
			} else if (msg.what == 1) {
				if (pins.size() == 0) {
					if (mProtocol != null) {
						mProtocol.queryProtocolVersion();
					}
					if (getActivity() != null) {
						Toast.makeText(getActivity(), "Retry it!",
								Toast.LENGTH_SHORT).show();
						mHandler.sendEmptyMessageDelayed(2, timeout);
					}
				}
			} else if (msg.what == 2) {
				if (pins.size() == 0) {
					if (mProtocol != null) {
						mProtocol.queryProtocolVersion();
					}
					if (getActivity() != null) {
						Toast.makeText(getActivity(), "Retry it again!",
								Toast.LENGTH_SHORT).show();
					}
					mTimer.schedule(mTimerTask, timeout);
					timerFlag = true;
				}
			}

			return true;
		}
	};

	Handler mHandler = new Handler(mHandlerCallback);

	protected void deviceRssiStateChange(String deviceAddress, int rssi,
			int state) {
		if (state == 0) {
			if (deviceAddress.equals(mDevice.address)) {
				mDevice.rssi = rssi;
				textRssi.setText("Rssi : " + rssi);
			}
		}
	}

	protected void deviceConnectStateChange(String deviceAddress, int state) {
		if (state == BluetoothProfile.STATE_CONNECTED) {
			Toast.makeText(getActivity(), "Connected", Toast.LENGTH_SHORT)
					.show();

			if (textRssi != null) {
				textRssi.postDelayed(new Runnable() {

					@Override
					public void run() {
						if (mProtocol != null) {

							// 1. queryProtocolVersion
							// 2. queryTotalPinCount
							// 3. queryPinAll
							// 4. end with "ABC"
							mProtocol.queryProtocolVersion();
						}
						mHandler.sendEmptyMessageDelayed(1, timeout);
					}
				}, 300);
			}

		} else if (state == BluetoothProfile.STATE_DISCONNECTED) {

		}
	}

	@Override
	public void protocolDidReceiveCustomData(int[] data, int length) {
		Log.e(TAG, "protocolDidReceiveCustomData data : " + data
				+ ", length : " + length);

		final int count = data.length;

		char[] chars = new char[count];

		for (int i = 0; i < count; i++) {
			chars[i] = (char) data[i];
		}

		String temp = new String(chars);
		Log.e(TAG, "temp : " + temp);
		if (temp.contains("ABC")) {
			if (getActivity() != null) {
				getActivity().runOnUiThread(new Runnable() {
					// removed loading and let the listview working

					@Override
					public void run() {
						if (mLoading != null) {
							mLoading.setVisibility(View.GONE);
						}
						if (changeValues != null) {
							final int count = pins.size();
							for (int i = 0; i < count; i++) {
								int key = pins.keyAt(i);
								PinInfo pInfo = pins.get(key);
								PinInfo changedPinInfo = changeValues.get(key
										+ "");

								if (changedPinInfo != null) {
									pInfo.mode = changedPinInfo.mode;
									pInfo.value = changedPinInfo.value;
								}
								refreshList(pInfo);
							}
							changeValues = null;
							isFirstReadPin = false;
						}
						pins_list.setEnabled(true);

					}
				});
			}
		}

	}

	@Override
	public void protocolDidReceiveProtocolVersion(int major, int minor,
			int bugfix) {
		Log.e(TAG, "major : " + major + ", minor : " + minor + ", bugfix : "
				+ bugfix);

		System.out.println(timerFlag);
		if (timerFlag == true)
			mTimerTask.cancel();

		if (mProtocol != null) {
			int[] data = { 'B', 'L', 'E' };
			mProtocol.sendCustomData(data, 3);

			if (textRssi != null) {
				textRssi.postDelayed(new Runnable() {

					@Override
					public void run() {
						mProtocol.queryTotalPinCount();
					}
				}, 300);
			}

		}
	}

	@Override
	public void protocolDidReceiveTotalPinCount(int count) {
		Log.e(TAG, "protocolDidReceiveTotalPinCount count : " + count);
		if (mProtocol != null) {
			mProtocol.queryPinAll();
		}
	}

	@Override
	public void protocolDidReceivePinCapability(int pin, int value) {
		Log.e(TAG, "protocolDidReceivePinCapability pin : " + pin
				+ ", value : " + value);

		if (value == 0) {
			Log.e(TAG, " - Nothing");
		} else {
			if (pins == null) {
				return;
			}
			PinInfo pinInfo = new PinInfo();
			pinInfo.pin = pin;

			ArrayList<Integer> modes = new ArrayList<Integer>();

			modes.add(INPUT);

			if ((value & PIN_CAPABILITY_DIGITAL) == PIN_CAPABILITY_DIGITAL) {
				Log.e(TAG, " - DIGITAL (I/O)");
				modes.add(OUTPUT);
			}

			if ((value & PIN_CAPABILITY_ANALOG) == PIN_CAPABILITY_ANALOG) {
				Log.e(TAG, " - ANALOG");
				modes.add(ANALOG);
			}

			if ((value & PIN_CAPABILITY_PWM) == PIN_CAPABILITY_PWM) {
				Log.e(TAG, " - PWM");
				modes.add(PWM);
			}

			if ((value & PIN_CAPABILITY_SERVO) == PIN_CAPABILITY_SERVO) {
				Log.e(TAG, " - SERVO");
				modes.add(SERVO);
			}

			final int count = modes.size();
			pinInfo.modes = new int[count];
			for (int i = 0; i < count; i++) {
				pinInfo.modes[i] = modes.get(i);
			}

			pins.put(pin, pinInfo);
			modes.clear();

			refreshList(pinInfo);
		}

	}

	@Override
	public void protocolDidReceivePinMode(int pin, int mode) {
		Log.e(TAG, "protocolDidReceivePinCapability pin : " + pin + ", mode : "
				+ mode);
		if (pins == null) {
			return;
		}

		PinInfo pinInfo = pins.get(pin);
		pinInfo.mode = mode;

		refreshList(pinInfo);
	}

	@Override
	public void protocolDidReceivePinData(int pin, int mode, int value) {
		byte _mode = (byte) (mode & 0x0F);

//		Log.e(TAG, "protocolDidReceivePinData pin : " + pin + ", _mode : "
//				+ _mode + ", value : " + value);

		if (pins == null) {
			return;
		}

		if (isFirstReadPin) {
			PinInfo pinInfo = new PinInfo();
			pinInfo.pin = pin;
			pinInfo.mode = _mode;
			if ((_mode == INPUT) || (_mode == OUTPUT))
				pinInfo.value = value;
			else if (_mode == ANALOG)
				pinInfo.value = ((mode >> 4) << 8) + value;
			else if (_mode == PWM)
				pinInfo.value = value;
			else if (_mode == SERVO)
				pinInfo.value = value;
			changeValues.put(pin + "", pinInfo);
		} else {
			PinInfo pinInfo = pins.get(pin);
			pinInfo.mode = _mode;
			if ((_mode == INPUT) || (_mode == OUTPUT))
				pinInfo.value = value;
			else if (_mode == ANALOG)
				pinInfo.value = ((mode >> 4) << 8) + value;
			else if (_mode == PWM)
				pinInfo.value = value;
			else if (_mode == SERVO)
				pinInfo.value = value;
			refreshList(pinInfo);
		}
	}

	protected void refreshList(final PinInfo pin) {
		if (textRssi != null) {
			textRssi.postDelayed(new Runnable() {
				@Override
				public void run() {

					if (mAdapter == null) {
						if (getActivity() == null) {
							return;
						}
						mAdapter = new PinAdapter(getActivity(), pins);
					}
					if (list_pins_views != null) {
						View view = list_pins_views.get(pin.pin + "");
						if (view == null) {
							if (pins_list == null) {
								return;
							}
							view = mAdapter.getView(pin.pin, view, null);
							LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
									LinearLayout.LayoutParams.MATCH_PARENT,
									LinearLayout.LayoutParams.WRAP_CONTENT);
							params.setMargins(10, 5, 10, 5);
							pins_list.addView(view, params);
							list_pins_views.put("" + pin.pin, view);
						} else {
							view = mAdapter.getView(pin.pin, view, null);
						}
					}
				}
			}, 50);
		}
	}

	class PinAdapter extends BaseAdapter {

		SparseArray<PinInfo> data = null;
		Context context;
		LayoutInflater mInflater;

		public PinAdapter(Context context, SparseArray<PinInfo> data) {
			this.data = data;
			this.context = context;
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {
			if (data != null) {
				return data.size();
			}
			return 0;
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		@Override
		public View getView(int position, View contentView, ViewGroup arg2) {

			PinInfo pinInfo = data.get(position);

			ViewHolder holder = null;
			if (contentView == null) {
				contentView = mInflater.inflate(R.layout.standardview_item_ex,
						null);
				holder = new ViewHolder();
				holder.pin = (TextView) contentView.findViewById(R.id.pin);
				holder.mode = (Button) contentView.findViewById(R.id.io_mode);
				holder.servo = (SeekBar) contentView
						.findViewById(R.id.progressbar);
				holder.analog = (TextView) contentView
						.findViewById(R.id.number);
				holder.digitol = (Switch) contentView
						.findViewById(R.id.switcher);
				contentView.setTag(holder);
			} else {
				holder = (ViewHolder) contentView.getTag();
			}

			String fix = "";
			if (pinInfo.pin < 10) {
				fix = "0";
			}
			holder.pin.setText("Pin:\t" + fix + pinInfo.pin);
			holder.mode.setText(getStateStr(pinInfo.mode));
			holder.mode.setTag(position);
			holder.mode.setOnClickListener(mModeClickListener);
			setModeAction(holder, pinInfo);
			return contentView;
		}

		private void setModeAction(ViewHolder holder, PinInfo pinInfo) {

			holder.analog.setVisibility(View.GONE);
			holder.analog.setTag(pinInfo.pin);

			holder.digitol.setVisibility(View.GONE);
			holder.digitol.setTag(pinInfo.pin);

			holder.servo.setVisibility(View.GONE);
			holder.servo.setTag(pinInfo.pin);

			switch (pinInfo.mode) {
			case IRBLProtocol.INPUT:
				holder.digitol.setVisibility(View.VISIBLE);
				holder.digitol.setEnabled(false);
				holder.digitol.setThumbResource(android.R.color.darker_gray);
				if (pinInfo.value == 1) {
					holder.digitol.setChecked(true);
				} else {
					holder.digitol.setChecked(false);
				}
				break;
			case IRBLProtocol.OUTPUT:
				holder.digitol.setVisibility(View.VISIBLE);
				holder.digitol.setThumbResource(R.color.blue);
				holder.digitol.setEnabled(true);
				if (pinInfo.value == 1) {
					holder.digitol.setChecked(true);
				} else {
					holder.digitol.setChecked(false);
				}
				holder.digitol
						.setOnCheckedChangeListener(mDigitolValueChangeListener);
				break;
			case IRBLProtocol.ANALOG:
				holder.analog.setVisibility(View.VISIBLE);
				holder.analog.setText("" + pinInfo.value);
				break;
			case IRBLProtocol.SERVO:
			case IRBLProtocol.PWM:
				if (pinInfo.mode == SERVO) {
					holder.servo.setMax(130);
				} else {
					holder.servo.setMax(130);
				}
				holder.servo.setVisibility(View.VISIBLE);
				holder.servo.setProgress(pinInfo.value);
				holder.servo.setOnSeekBarChangeListener(new SeekBarChange());
				break;
			}
		}

		OnCheckedChangeListener mDigitolValueChangeListener = new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton view, boolean value) {

				if (view.isEnabled()) {
					Integer key = (Integer) view.getTag();
					if (key != null) {
						if (mProtocol != null) {
							mProtocol.digitalWrite(key.intValue(),
									value ? (byte) 1 : 0);
						}
					}
				}

			}
		};

		class SeekBarChange implements OnSeekBarChangeListener {
			int value;

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {

				Log.e(TAG, "value : " + value);
				if (fromUser) {

					value = progress;
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

				Integer key = (Integer) seekBar.getTag();
				if (key != null) {
					if (mProtocol != null) {
						PinInfo pinInfo = data.get(key);
						if (pinInfo.mode == PWM) {
							mProtocol.analogWrite(key.intValue(), value);
						} else {
							mProtocol.servoWrite(key.intValue(), value);
						}
					}
				}

			}

		}

		OnClickListener mModeClickListener = new OnClickListener() {

			@Override
			public void onClick(View view) {

				Integer index = (Integer) view.getTag();
				if (index != null) {
					int key = index;
					PinInfo pinInfo = data.get(key);
					showModeSelect(pinInfo);
				}
			}
		};

		class ViewHolder {
			TextView pin;
			Button mode;
			Switch digitol;
			SeekBar servo;
			TextView analog;
		}
	}

	RelativeLayout select_window;

	protected void showModeSelect(PinInfo pinInfo) {
		if (getActivity() != null) {
			LinearLayout modes_area = null;
			final int modes_area_id = 0x123ff;
			if (select_window == null) {
				select_window = new RelativeLayout(getActivity());
				select_window.setBackgroundColor(0x4f000000);
				select_window.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View arg0) {
						select_window.setVisibility(View.INVISIBLE);
					}
				});

				modes_area = new LinearLayout(getActivity());
				modes_area.setId(modes_area_id);
				modes_area.setBackgroundColor(Color.WHITE);
				modes_area.setOrientation(LinearLayout.VERTICAL);

				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT,
						RelativeLayout.LayoutParams.WRAP_CONTENT);
				params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
						RelativeLayout.TRUE);
				select_window.addView(modes_area, params);

				getActivity().addContentView(
						select_window,
						new LayoutParams(LayoutParams.MATCH_PARENT,
								LayoutParams.WRAP_CONTENT));
			} else {
				modes_area = (LinearLayout) select_window
						.findViewById(modes_area_id);
			}

			select_window.setVisibility(View.INVISIBLE);
			modes_area.removeAllViews();

			for (int b : pinInfo.modes) {
				String text = getStateStr(b);
				if (text != null) {
					final int btn_mode = b;
					final int btn_pin = pinInfo.pin;
					Button btn = createModeButton(text);
					LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
					params.setMargins(5, 5, 5, 5);
					btn.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View arg0) {

							if (mProtocol != null) {
								mProtocol.setPinMode(btn_pin, btn_mode);
							}

							select_window.setVisibility(View.INVISIBLE);

						}
					});
					modes_area.addView(btn, params);
				}
			}

			AlphaAnimation animation = new AlphaAnimation(0, 1);
			animation.setDuration(350);
			animation.setInterpolator(new DecelerateInterpolator());
			animation.setAnimationListener(new AnimationListener() {

				@Override
				public void onAnimationStart(Animation arg0) {
					select_window.setVisibility(View.VISIBLE);
				}

				@Override
				public void onAnimationRepeat(Animation arg0) {

				}

				@Override
				public void onAnimationEnd(Animation arg0) {

				}
			});
			select_window.startAnimation(animation);
		}
	}

	protected Button createModeButton(String text) {

		Button btn = new Button(getActivity());

		btn.setBackgroundResource(R.drawable.button_selector);

		btn.setPadding(20, 5, 20, 5);

		btn.setText(text);

		return btn;
	}

	protected String getStateStr(int mode) {
		switch (mode) {
		case IRBLProtocol.INPUT:
			return STR_INPUT;
		case IRBLProtocol.OUTPUT:
			return STR_OUTPUT;
		case IRBLProtocol.ANALOG:
			return STR_ANALOG;
		case IRBLProtocol.SERVO:
			return STR_SERVO;
		case IRBLProtocol.PWM:
			return STR_PWM;
		}
		return null;
	}

	protected double updateAccelerometerSeries(PinInfo pinX, PinInfo pinY, PinInfo pinZ, double time, int timeStep) {
//	    Log.i("What Values?", String.format("%d, %d, %d", pinX.value, pinY.value, pinZ.value));
	    double mag = Math.pow( convert2Gs(pinX.value, zeroX), 2 ) + Math.pow( convert2Gs(pinY.value, zeroY), 2 ) + Math.pow( convert2Gs(pinZ.value, zeroZ), 2 );
        mag = Math.pow(mag, 0.5);
	    accelerometer_series.appendData(new DataPoint((time * timeStep)/1000.0, mag), true, maxPoints);
//	    force = mag*mass;
	    return mag;
    }

    protected double convert2Gs (int rawSensor, int zero) {
	    return ((rawSensor - zero) * 200.0 / zero);
    }

    protected double[] updateECGSeries(PinInfo pinH, double time, int timeStep) {
//	    Log.i("What Values?", String.format("%d, %d, %d", pinX.value, pinY.value, pinZ.value));

        ecgroll.append(pinH.value);
		double mag = ecgroll.avg();

		double[] BPM_CI = calcBPM(mag, timeStep);
		double BPM = BPM_CI[0];
		double CI = BPM_CI[1];
		long runningTotal = (long) BPM_CI[2];

		double[] HRV_RPE = calcHRV(BPM,runningTotal);
		double HRV = HRV_RPE[0];
		double RPE = HRV_RPE[1];

		ecg_series.appendData(new DataPoint((time * timeStep)/1000.0, mag), true, maxPoints);
		//currently using mag, which shows pulse and not HR
		hrv_series.appendData(new DataPoint((time * timeStep)/1000.0, HRV), true, maxPoints);

		double[] outs = {mag, BPM, HRV, CI, RPE};
		return outs;
	}

	protected double[] calcBPM(double mag, int timeStep) {
		//code adapted from WorldFamousElectronics/PulseSensor_Amped_Arduino
//		long runningTotal = 0;                  // clear the runningTotal variable

		sampleCounter += timeStep;                         // keep track of the time in mS with this variable
		double N = sampleCounter - lastBeatTime;       // monitor the time since the last beat to avoid noise
		if (mag < thresh && N > (IBI / 5) * 3) {       // avoid dichrotic noise by waiting 3/5 of last IBI
			if (mag < T) {                        // T is the trough
				T = mag;                         // keep track of lowest point in pulse wave
			}
		}

		if (mag > thresh && mag > P) {          // thresh condition helps avoid noise
			P = mag;                             // P is the peak
		}                                        // keep track of highest point in pulse wave

//		  NOW IT'S TIME TO LOOK FOR THE HEART BEAT
		//signal surges up in value every time there is a pulse
//		if (N > 250) {                                   // avoid high frequency noise
		Log.i("N", String.format("%f", N));
		Log.i("IBI", String.format("%f", IBI)); //if 20ms apart, is every 2 ticks
		Log.i("thresh", String.format("%f", thresh)); //if 20ms apart, is every 2 ticks

		if ((mag > thresh) && (Pulse == false) && (N > (IBI / 5) * 3)) {
			Log.i("mag > thresh?", "YIPPEE!");
			Pulse = true;                               // set the Pulse flag when we think there is a pulse
			Log.i("LBT, SC", String.format("%f, %f", lastBeatTime, sampleCounter));
			IBI = sampleCounter - lastBeatTime;         // measure time between beats in mS = R-R interval!
			lastBeatTime = sampleCounter;               // keep track of time for next pulse
//				if (secondBeat) {                        // if this is the second beat, if secondBeat == TRUE
//					secondBeat = false;                  // clear secondBeat flag
//					for (int i = 0; i <= 9; i++) {             // seed the running total to get a realistic BPM at startup
//						rate[i] = IBI;
//					}
//				}

//				if (firstBeat) {                         // if it's the first time we found a beat, if firstBeat == TRUE
//					firstBeat = false;                   // clear firstBeat flag
//					secondBeat = true;                   // set the second beat flag
//					//						sei();                               // enable interrupts again
//					//						return;                              // IBI value is unreliable so discard it
//				}

			// keep a running total of the last 10 IBI values
//				for (int i = 0; i <= 8; i++) {                // shift data in the rate array
//					rate[i] = rate[i + 1];                  // and drop the oldest IBI value
//					runningTotal += rate[i];              // add up the 9 oldest IBI values
//				}
//
//				rate[9] = IBI;                          // add the latest IBI to the rate array
			bpmrtrw.append(IBI);
			runningTotal = bpmrtrw.avg();

//				runningTotal += rate[9];                // add the latest IBI to runningTotal
//				runningTotal /= 10;                     // average the last 10 IBI values
//				Log.i("IBI", String.format("%f", IBI));
			Log.i("Runningtotal", String.format("%f", runningTotal));

			if (runningTotal > 0) {
				//HR is 60000/IBI, so runningTotal might be the new IBI? If so...
				BPM = 60000 / runningTotal;               // how many beats can fit into a minute? that's BPM
				Log.i("BPM", String.format("%f", BPM));
			}
			CI = BPM * SV / (SA);
		}
//		}

		if (mag < thresh && Pulse == true){   // when the values are going down, the beat is over
			// turn off pin 13 LED
			Pulse = false;                         // reset the Pulse flag so we can do it again
			amp = P - T;                           // get amplitude of the pulse wave
			thresh = amp/2 + T;                    // set thresh at 50% of the amplitude
			P = thresh;                            // reset these for next time
			T = thresh;
		}

		if (N > 2500){                           // if 2.5 seconds go by without a beat
			thresh = 512;                          // set thresh default
			P = 512;                               // set P default
			T = 512;                               // set T default
			lastBeatTime = sampleCounter;          // bring the lastBeatTime up to date
			firstBeat = true;                      // set these to avoid noise
			secondBeat = false;                    // when we get the heartbeat back
		}
//
		Log.i("What Values?", String.format("%f, %f, %f, %f", P, T, mag, BPM));

		double[] outs = {BPM, CI, runningTotal};
		return outs;
	}

	protected double[] calcHRV(double BPM, long runningTotal) {

//		adapted from https://github.com/jkeech/BioInk/blob/master/src/com/vitaltech/bioink/User.java
		if (BPM > 0) {
			double x = (60000.0 / BPM) - IBI;
			double y = Math.pow(x, 2);
			hrvrw.append(y);
			Log.i("x, ", "" + x + ", " + y + ", " + hrvrw.avg() + hrvrw.toString());
			HRV = Math.pow(hrvrw.avg(), 0.5);
		}
//		boolean hrv_active = false;
//		List<Long> rrq;
//		final int qsize = 20;
//		rrq = Collections.synchronizedList(new ArrayList<Long>());
//
////		take addRR out of the separate function?
//		int size = rrq.size();
//		//check if new RR interval value has been received. if so, add to the list
//		if(rrq.isEmpty()){
//			rrq.add(runningTotal); //where runningTotal is newest RR interval value
//		}else if(runningTotal != rrq.get(size - 1)){
//			if(rrq.size() < qsize){
//				rrq.add(runningTotal);
//			}else{
//				//list is full, remove oldest value and add new to the end of the list
//				rrq.remove(0);
//				rrq.add(runningTotal);
//			} //check if hrv was inactive and needs to be activated
//			if(qsize == rrq.size() && !hrv_active){
//				hrv_active = true;
//			}
			RPE = BPM/10;
//
//		}else{
//			//no new value to be added, exit
//		}
//
////					calculateHRV without the function
//		float rmssd = 0f;
//		if(hrv_active){
//			float ssd = 0;
//			float previous = -1;
//
//			synchronized(rrq) {
//				Iterator<Long> i = rrq.iterator(); // Must be in synchronized block
//
//				while(i.hasNext()){
//					float rri = Math.abs(i.next());
//					if(previous == -1){
//						previous = Math.abs(rri);
//						continue;
//					}else{
//						//calculate consecutive difference
//						float diff = previous - rri;
//						diff = diff * diff;
//						//add the new square difference to the total
//						ssd = ssd + diff;
//						//update the previous value
//						previous = rri;
//					}
//				}
//			}
//			//calculate the MSSD
//			ssd = ssd / (rrq.size() - 1);
//			//calculate the RMSSD value and update it to the HRV of the user
//			rmssd = (float) Math.sqrt(ssd);
////							rmssd = Math.max(Math.min(rmssd, DataProcess.MAX_HRV), 0);
////							*not sure if this ^ is useful...we don't have a data process module like this proj did?
//			HRV = rmssd;
//		}

//						return rmssd;

//					QS = true;                              // set Quantified Self flag
		// QS FLAG IS NOT CLEARED INSIDE THIS ISR

		//		void interruptSetup()  {  // CHECK OUT THE Timer_Interrupt_Notes TAB FOR MORE ON INTERRUPTS
		//			// Initializes Timer2 to throw an interrupt every 2mS.
		//			TCCR2A = 0x02;     // DISABLE PWM ON DIGITAL PINS 3 AND 11, AND GO INTO CTC MODE
		//			TCCR2B = 0x06;     // DON'T FORCE COMPARE, 256 PRESCALER
		//			OCR2A = 0X7C;      // SET THE TOP OF THE COUNT TO 124 FOR 500Hz SAMPLE RATE
		//			TIMSK2 = 0x02;     // ENABLE INTERRUPT ON MATCH BETWEEN TIMER2 AND OCR2A
		//			sei();             // MAKE SURE GLOBAL INTERRUPTS ARE ENABLED

		double[] outs = {HRV, RPE};
		return outs;
	}

//
//// THIS IS THE TIMER 2 INTERRUPT SERVICE ROUTINE.
//// Timer 2 makes sure that we take a reading every 2 miliseconds
//		ISR(TIMER2_COMPA_vect){                         // triggered when Timer2 counts to 124
//			cli();                                      // disable interrupts while we do this
//			Signal = analogRead(pulsePin);              // read the Pulse Sensor
//			sampleCounter += 2;                         // keep track of the time in mS with this variable
//			int N = sampleCounter - lastBeatTime;       // monitor the time since the last beat to avoid noise
//
//			//  find the peak and trough of the pulse wave
//			if(Signal < thresh && N > (IBI/5)*3){       // avoid dichrotic noise by waiting 3/5 of last IBI
//				if (Signal < T){                        // T is the trough
//					T = Signal;                         // keep track of lowest point in pulse wave
//				}
//			}
//
//			if(Signal > thresh && Signal > P){          // thresh condition helps avoid noise
//				P = Signal;                             // P is the peak
//			}                                        // keep track of highest point in pulse wave
//
//			//  NOW IT'S TIME TO LOOK FOR THE HEART BEAT
//			// signal surges up in value every time there is a pulse
//			if (N > 250){                                   // avoid high frequency noise
//				if ( (Signal > thresh) && (Pulse == false) && (N > (IBI/5)*3) ){
//					Pulse = true;                               // set the Pulse flag when we think there is a pulse
//					digitalWrite(blinkPin,HIGH);                // turn on pin 13 LED
//					IBI = sampleCounter - lastBeatTime;         // measure time between beats in mS
//					lastBeatTime = sampleCounter;               // keep track of time for next pulse
//
//					if(secondBeat){                        // if this is the second beat, if secondBeat == TRUE
//						secondBeat = false;                  // clear secondBeat flag
//						for(int i=0; i<=9; i++){             // seed the running total to get a realisitic BPM at startup
//							rate[i] = IBI;
//						}
//					}
//
//					if(firstBeat){                         // if it's the first time we found a beat, if firstBeat == TRUE
//						firstBeat = false;                   // clear firstBeat flag
//						secondBeat = true;                   // set the second beat flag
//						sei();                               // enable interrupts again
//						return;                              // IBI value is unreliable so discard it
//					}
//
//
//					// keep a running total of the last 10 IBI values
//					word runningTotal = 0;                  // clear the runningTotal variable
//
//					for(int i=0; i<=8; i++){                // shift data in the rate array
//						rate[i] = rate[i+1];                  // and drop the oldest IBI value
//						runningTotal += rate[i];              // add up the 9 oldest IBI values
//					}
//
//					rate[9] = IBI;                          // add the latest IBI to the rate array
//					runningTotal += rate[9];                // add the latest IBI to runningTotal
//					runningTotal /= 10;                     // average the last 10 IBI values
//					BPM = 60000/runningTotal;               // how many beats can fit into a minute? that's BPM!
//					QS = true;                              // set Quantified Self flag
//					// QS FLAG IS NOT CLEARED INSIDE THIS ISR
//				}
//			}
//
//			if (Signal < thresh && Pulse == true){   // when the values are going down, the beat is over
//				digitalWrite(blinkPin,LOW);            // turn off pin 13 LED
//				Pulse = false;                         // reset the Pulse flag so we can do it again
//				amp = P - T;                           // get amplitude of the pulse wave
//				thresh = amp/2 + T;                    // set thresh at 50% of the amplitude
//				P = thresh;                            // reset these for next time
//				T = thresh;
//			}
//
//			if (N > 2500){                           // if 2.5 seconds go by without a beat
//				thresh = 530;                          // set thresh default
//				P = 512;                               // set P default
//				T = 512;                               // set T default
//				lastBeatTime = sampleCounter;          // bring the lastBeatTime up to date
//				firstBeat = true;                      // set these to avoid noise
//				secondBeat = false;                    // when we get the heartbeat back
//			}
//
//			sei();                                   // enable interrupts when youre done!
//		}// end isr
//    }

    protected void updateAccelText(double mag) {
        textAccelerometer.setText(String.format("Acceleration: %.1f", mag));
    }

    protected void updateECGText(double mag) {
        textECG.setText(String.format("Heart Rate: %.0f", mag));
    }

	protected void updateOtherMetricsText(double force, double CI, double HRV, double RPE) {
		textForce.setText(String.format("Force: %.0f", force));
		textCI.setText(String.format("CI: %.1f", CI));
		textHRV.setText(String.format("HRV: %.1f", HRV));
		textRPE.setText(String.format("RPE: %.0f", RPE));
	}

	protected void updateFatigueIndicators(double HR, double RPE, double HRV, double CI, double force) {

		fatcounter = 0; //reset every time

		if(HR > 80){ //set this based on avg bpm
			HRFatButton.setBackgroundColor(Color.RED);
			fatcounter++;
		}else{
			HRFatButton.setBackgroundColor(Color.GREEN);
		}

		if(RPE > 14){
			RPEFatButton.setBackgroundColor(Color.RED);
			fatcounter++;
		}else{
			RPEFatButton.setBackgroundColor(Color.GREEN);
		}

		if(HRV > 59.3){
			HRVFatButton.setBackgroundColor(Color.RED);
			fatcounter++;
		}else{
			HRVFatButton.setBackgroundColor(Color.GREEN);
		}

		if(CI > 4){
			CIFatButton.setBackgroundColor(Color.RED);
			fatcounter++;
		}else{
			CIFatButton.setBackgroundColor(Color.GREEN);
		}

		if(force > 1160){
			ForceFatButton.setBackgroundColor(Color.RED);
			fatcounter++;
		}else{
			ForceFatButton.setBackgroundColor(Color.GREEN);
		}

		if(fatcounter>0){
			textFatIndCount.setText(String.format("Based on %d of 5 indicators, you may be fatigued.", fatcounter));
		}else{
			textFatIndCount.setText(String.format("Based on %d of 5 indicators, you are likely not fatigued.", fatcounter));
		}
	}


	private class RollingWindow {
		double sum;
		double[] points;
		int size;
		int ind;

		public RollingWindow(int maxSize) {
			this.size = 0;
			this.points = new double[maxSize];
			this.sum = 0;
			this.ind = 0;
		}

		public void append(double newPoint) {
			this.sum += newPoint;
			int next = ((this.ind + 1) % this.points.length);
			if (this.size < this.points.length) {
				this.points[ind] = newPoint;
				this.size++;
			} else {
				this.sum -= points[ind];
				points[ind] = newPoint;
			}
			this.ind = next;
		}

		public double avg() {
			return size > 0 ? sum/size : 0;
		}

		public boolean isFull() {
			return this.size == this.points.length;
		}

		public void clear() {
			this.size = 0;
			this.sum = 0;
			this.ind = 0;
		}

		@Override
		public String toString() {
			return String.format("Sum: %f, Size: %d", this.sum, this.size);
		}
	}
}