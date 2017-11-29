package com.redbear.redbearbleclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

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

    Button zeroAcc;
    TextView textAccelerometer;
    TextView textECG;

    int zeroX = 512;
    int zeroY = 512;
    int zeroZ = 512;
    final int windowSize = 10;
    final int timeStep = 50; //ms
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

	public StandardViewFragmentForPinsEx() {
	}

	public StandardViewFragmentForPinsEx(Device mDevice, RedBearService mRedBearService) {
		this.mDevice = mDevice;
		pins = new SparseArray<PinInfo>();
		changeValues = new HashMap<String, PinInfo>();
		list_pins_views = new HashMap<String, View>();
		this.mRedBearService = mRedBearService;

        ecg_series = new LineGraphSeries<DataPoint>();
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

//		pins_list.setVisibility(View.GONE);
//        GraphView ecg_graph = (GraphView) view.findViewById(R.id.ecg_graph);
//        ecg_graph.addSeries(ecg_series);

        textAccelerometer = (TextView) view.findViewById(R.id.Accelerometer);
        textECG = (TextView) view.findViewById(R.id.ECG);

        zeroAcc = (Button) view.findViewById(R.id.zeroAcc);

        GraphView accelerometer_graph = (GraphView) view.findViewById(R.id.accelerometer_graph);
        accelerometer_series = new LineGraphSeries<DataPoint>();
        accelerometer_graph.addSeries(accelerometer_series);
        accelerometer_graph.setEnabled(true);
        Viewport viewportA = accelerometer_graph.getViewport();
        viewportA.setXAxisBoundsManual(true);
        viewportA.setMinX(0);
        viewportA.setMaxX(windowSize);
        viewportA.setScrollable(true);

        GraphView ecg_graph = (GraphView) view.findViewById(R.id.ecg_graph);
        ecg_series = new LineGraphSeries<DataPoint>();
        ecg_graph.addSeries(ecg_series);
        ecg_graph.setEnabled(true);
        Viewport viewportE = ecg_graph.getViewport();
        viewportE.setXAxisBoundsManual(true);
        viewportE.setMinX(0);
        viewportE.setMaxX(windowSize);
        viewportE.setScrollable(true);

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
                Log.d(TAG, "HELLO: " + pins.size());
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
                                Log.i(TAG, String.format("x: %d\ny: %d\nz: %d\n", pinX.value, pinY.value, pinZ.value));
                                double am = updateAccelerometerSeries(pinX, pinY, pinZ, time++, timeStep);
                                Log.i(TAG, "Acc Series: " + time + ", " + accelerometer_series.getHighestValueX());
                                Log.i(TAG, String.format("h: %d", pinH.value));
                                double em = updateECGSeries(pinH, time++, timeStep);
                                Log.i(TAG, "ECG Series: " + time + ", " + ecg_series.getHighestValueX());
//                                u
// '''' dateAccelText(am);
                                updateECGText(em);
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

		Log.e(TAG, "protocolDidReceivePinData pin : " + pin + ", _mode : "
				+ _mode + ", value : " + value);

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
	    return mag;
    }

    protected double convert2Gs (int rawSensor, int zero) {
	    return ((rawSensor - zero) * 200.0 / zero);
    }

    protected double updateECGSeries(PinInfo pinH, double time, int timeStep) {
//	    Log.i("What Values?", String.format("%d, %d, %d", pinX.value, pinY.value, pinZ.value));
        double mag = pinH.value;
//        mag = Math.pow(mag, 0.5);

        ecg_series.appendData(new DataPoint((time * timeStep)/1000.0, mag), true, maxPoints);
        return mag;
    }

    protected void updateAccelText(double mag) {
        textAccelerometer.setText(String.format("Acceleration: %.0f", mag));
    }

    protected void updateECGText(double mag) {
        textECG.setText(String.format("Heart Rate: %.0f", mag));
    }


}