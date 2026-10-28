/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 * Copyright 2021 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.microedition.lcdui.keyboard;

import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_A;
import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_B;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.lcdui.overlay.Overlay;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.apex.ApexPrefs;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

public class VirtualKeyboard implements Overlay, Runnable {
	private static final String TAG = VirtualKeyboard.class.getSimpleName();

	private static final String ARROW_LEFT = "\u2190";
	private static final String ARROW_UP = "\u2191";
	private static final String ARROW_RIGHT = "\u2192";
	private static final String ARROW_DOWN = "\u2193";
	private static final String ARROW_UP_LEFT = "\u2196";
	private static final String ARROW_UP_RIGHT = "\u2197";
	private static final String ARROW_DOWN_LEFT = "\u2199";
	private static final String ARROW_DOWN_RIGHT = "\u2198";

	private static final int LAYOUT_SIGNATURE = 0x564B4C00;
	private static final int LAYOUT_VERSION = 3;
	public static final int LAYOUT_EOF = -1;
	public static final int LAYOUT_KEYS = 0;
	public static final int LAYOUT_SCALES = 1;
	@SuppressWarnings("unused")
	public static final int LAYOUT_COLORS = 2;
	public static final int LAYOUT_TYPE = 3;

	private static final int OVAL_SHAPE = 0;
	private static final int RECT_SHAPE = 1;
	public static final int ROUND_RECT_SHAPE = 2;

	public static final int TYPE_CUSTOM = 0;
	private static final int TYPE_PHONE = 1;
	private static final int TYPE_PHONE_ARROWS = 2;
	private static final int TYPE_NUM_ARR = 3;
	private static final int TYPE_ARR_NUM = 4;
	private static final int TYPE_NUMBERS = 5;
	private static final int TYPE_ARROWS = 6;

	private static final float PHONE_KEY_ROWS = 5;
	private static final float PHONE_KEY_SCALE_X = 2.0f;
	private static final float PHONE_KEY_SCALE_Y = 0.75f;
	private static final long[] REPEAT_INTERVALS = {200, 400, 128, 128, 128, 128, 128};

	private static final int SCREEN = -1;
	private static final int KEY_NUM1 = 0;
	private static final int KEY_NUM2 = 1;
	private static final int KEY_NUM3 = 2;
	private static final int KEY_NUM4 = 3;
	private static final int KEY_NUM5 = 4;
	private static final int KEY_NUM6 = 5;
	private static final int KEY_NUM7 = 6;
	private static final int KEY_NUM8 = 7;
	private static final int KEY_NUM9 = 8;
	private static final int KEY_NUM0 = 9;
	private static final int KEY_STAR = 10;
	private static final int KEY_POUND = 11;
	private static final int KEY_SOFT_LEFT = 12;
	private static final int KEY_SOFT_RIGHT = 13;
	private static final int KEY_D = 14;
	private static final int KEY_C = 15;
	private static final int KEY_UP_LEFT = 16;
	private static final int KEY_UP = 17;
	private static final int KEY_UP_RIGHT = 18;
	private static final int KEY_LEFT = 19;
	private static final int KEY_RIGHT = 20;
	private static final int KEY_DOWN_LEFT = 21;
	private static final int KEY_DOWN = 22;
	private static final int KEY_DOWN_RIGHT = 23;
	private static final int KEY_FIRE = 24;
	private static final int KEY_A = 25;
	private static final int KEY_B = 26;
	private static final int KEY_MENU = 27;
	private static final int KEYBOARD_SIZE = 28;

	private static final float SCALE_SNAP_RADIUS = 0.05f;

	private static final int FEEDBACK_DURATION = 50;

	private final float[] keyScales = {
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
	};
	private final int[][] keyScaleGroups = {{
			KEY_UP_LEFT,
			KEY_UP,
			KEY_UP_RIGHT,
			KEY_LEFT,
			KEY_RIGHT,
			KEY_DOWN_LEFT,
			KEY_DOWN,
			KEY_DOWN_RIGHT
	}, {
			KEY_SOFT_LEFT,
			KEY_SOFT_RIGHT
	}, {
			KEY_A,
			KEY_B,
			KEY_C,
			KEY_D,
	}, {
			KEY_NUM1,
			KEY_NUM2,
			KEY_NUM3,
			KEY_NUM4,
			KEY_NUM5,
			KEY_NUM6,
			KEY_NUM7,
			KEY_NUM8,
			KEY_NUM9,
			KEY_NUM0,
			KEY_STAR,
			KEY_POUND
	}, {
			KEY_FIRE
	}, {
			KEY_MENU
	}};
	private final VirtualKey[] keypad = new VirtualKey[KEYBOARD_SIZE];
	// the average user usually has no more than 10 fingers...
	private final VirtualKey[] associatedKeys = new VirtualKey[10];
	private final int[] snapStack = new int[KEYBOARD_SIZE];

	private final Handler handler;
	private final ApexAnalog analog;
	private final ApexDpad dpad;
	private final ApexLetterPad letterPad;
	private final ApexFastForward ff;
	private final File saveFile;
	private final ProfileModel settings;
	private final RectF virtualScreen = new RectF();

	private Canvas target;
	private View overlayView;
	private boolean obscuresVirtualScreen;
	private boolean visible = true;
	private int layoutEditMode = LAYOUT_EOF;
	private int editedIndex;
	private float offsetX;
	private float offsetY;
	private float prevScaleX;
	private float prevScaleY;
	private RectF screen;
	private float keySize =
			Math.min(ContextHolder.getDisplayWidth(), ContextHolder.getDisplayHeight()) / 6.0f;
	private float snapRadius;
	private int layoutVariant;
	// dedo que esta editando (so um por vez) e dedo da barra de grade
	private int editPointer = -1;
	private int barPointer = -1;
	private final RectF barGrid = new RectF();
	private final RectF barMinus = new RectF();
	private final RectF barValue = new RectF();
	private final RectF barPlus = new RectF();
	private float[] gridLines = new float[0];

	public VirtualKeyboard(ProfileModel settings) {
		this.settings = settings;
		this.saveFile = new File(settings.dir + Config.MIDLET_KEY_LAYOUT_FILE);

		for (int i = KEY_NUM1; i < 9; i++) {
			keypad[i] = new VirtualKey(Canvas.KEY_NUM1 + i, Integer.toString(1 + i));
		}

		keypad[KEY_NUM0] = new VirtualKey(Canvas.KEY_NUM0, "0");
		keypad[KEY_STAR] = new VirtualKey(Canvas.KEY_STAR, "*");
		keypad[KEY_POUND] = new VirtualKey(Canvas.KEY_POUND, "#");

		keypad[KEY_SOFT_LEFT] = new VirtualKey(Canvas.KEY_SOFT_LEFT, "L");
		keypad[KEY_SOFT_RIGHT] = new VirtualKey(Canvas.KEY_SOFT_RIGHT, "R");

		keypad[KEY_A] = new EditKey(SE_KEY_SPECIAL_GAMING_A, "A");
		keypad[KEY_B] = new EditKey(SE_KEY_SPECIAL_GAMING_B, "B");
		keypad[KEY_C] = new VirtualKey(Canvas.KEY_END, "C");
		keypad[KEY_D] = new VirtualKey(Canvas.KEY_SEND, "D");

		keypad[KEY_UP_LEFT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_LEFT, ARROW_UP_LEFT);
		keypad[KEY_UP] = new VirtualKey(Canvas.KEY_UP, ARROW_UP);
		keypad[KEY_UP_RIGHT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_RIGHT, ARROW_UP_RIGHT);

		keypad[KEY_LEFT] = new VirtualKey(Canvas.KEY_LEFT, ARROW_LEFT);
		keypad[KEY_RIGHT] = new VirtualKey(Canvas.KEY_RIGHT, ARROW_RIGHT);

		keypad[KEY_DOWN_LEFT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_LEFT, ARROW_DOWN_LEFT);
		keypad[KEY_DOWN] = new VirtualKey(Canvas.KEY_DOWN, ARROW_DOWN);
		keypad[KEY_DOWN_RIGHT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_RIGHT, ARROW_DOWN_RIGHT);

		keypad[KEY_FIRE] = new VirtualKey(Canvas.KEY_FIRE, "F");
		keypad[KEY_MENU] = new MenuKey();
		for (int i = 0; i < keypad.length; i++) {
			keypad[i].imageId = ApexPrefs.KEY_IDS[i];
			keypad[i].arrow = i == KEY_UP_LEFT || i == KEY_UP || i == KEY_UP_RIGHT || i == KEY_LEFT
					|| i == KEY_RIGHT || i == KEY_DOWN_LEFT || i == KEY_DOWN || i == KEY_DOWN_RIGHT;
		}

		layoutVariant = readLayoutType();

		if (layoutVariant == -1) {
			layoutVariant = settings.vkType;
			if (layoutVariant == TYPE_CUSTOM) {
				layoutVariant = TYPE_NUM_ARR;
			}
		}
		resetLayout(layoutVariant);
		if (layoutVariant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException e) {
				e.printStackTrace();
				resetLayout(TYPE_NUM_ARR);
				layoutVariant = TYPE_NUM_ARR;
				saveLayout();
			}
		}
		analog = new ApexAnalog(settings);
		dpad = new ApexDpad(settings);
		letterPad = new ApexLetterPad(settings);
		ff = new ApexFastForward(settings);
		HandlerThread thread = new HandlerThread("MidletVirtualKeyboard");
		thread.start();
		handler = new Handler(thread.getLooper());
	}

	public void onLayoutChanged(int variant) {
		if (variant == TYPE_CUSTOM && isPhone()) {
			float min = screen.width();
			float max = screen.height();
			if (min > max) {
				float tmp = max;
				max = min;
				min = tmp;
			}

			float oldSize = min / 6.0f;
			float newSize = Math.min(oldSize, max / 12.0f);
			float s = oldSize / newSize;
			for (int i = 0; i < keyScales.length; i++) {
				keyScales[i] *= s;
			}
		}
		layoutVariant = variant;
		saveLayout();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void resetLayout(int variant) {
		switch (variant) {
			case TYPE_PHONE:
				for (int j = 0, len = keyScales.length; j < len;) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_NUM2, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_MENU, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
				break;
			case TYPE_PHONE_ARROWS:
				for (int j = 0, len = keyScales.length; j < len;) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_DOWN, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_RIGHT, KEY_FIRE, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_UP, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM2, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM4, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				break;
			case TYPE_NUM_ARR:
			default:
				Arrays.fill(keyScales, 1.0f);

				setSnap(KEY_DOWN_RIGHT, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_DOWN, KEY_DOWN_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_STAR, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_NUM0, KEY_STAR, RectSnap.EXT_EAST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
				break;
			case TYPE_ARR_NUM:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_POUND, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_NUM0, KEY_POUND, RectSnap.EXT_WEST, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
				break;
			case TYPE_NUMBERS:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_EAST, true);

				setSnap(KEY_UP, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, false);
				setSnap(KEY_UP_RIGHT, KEY_UP, RectSnap.EXT_EAST, false);
				setSnap(KEY_FIRE, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, false);
				setSnap(KEY_A, KEY_NUM4, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_NUM6, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_NUM7, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_NUM9, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
				break;
			case TYPE_ARROWS:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_EAST, true);

				setSnap(KEY_NUM1, KEY_NUM2, RectSnap.EXT_WEST, false);
				setSnap(KEY_NUM2, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, false);
				setSnap(KEY_NUM4, KEY_NUM1, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_NUM3, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM7, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM9, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_STAR, KEY_NUM7, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM0, KEY_NUM8, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_POUND, KEY_NUM9, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_A, KEY_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_DOWN_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_DOWN_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
				break;
		}
	}

	public int getLayout() {
		return layoutVariant;
	}

	public float getPhoneKeyboardHeight(float w, float h) {
		return PHONE_KEY_ROWS * getKeySize(w, h) * PHONE_KEY_SCALE_Y;
	}

	public void setLayout(int variant) {
		resetLayout(variant);
		if (variant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException ioe) {
				ioe.printStackTrace();
				resetLayout(layoutVariant);
				return;
			}
		}
		layoutVariant = variant;
		onLayoutChanged(variant);
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		overlayView.postInvalidate();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void saveLayout() {
		try (RandomAccessFile raf = new RandomAccessFile(saveFile, "rw")) {
			int variant = layoutVariant;
			if (variant != TYPE_CUSTOM && raf.length() > 16) {
				try {
					if (raf.readInt() != LAYOUT_SIGNATURE) {
						throw new IOException("file signature not found");
					}
					int version = raf.readInt();
					if (version < 1 || version > LAYOUT_VERSION) {
						throw new IOException("incompatible file version");
					}
					loop:while (true) {
						int block = raf.readInt();
						int length = raf.readInt();
						switch (block) {
							case LAYOUT_EOF:
								raf.seek(raf.getFilePointer() - 8);
								raf.writeInt(LAYOUT_TYPE);
								raf.writeInt(1);
								raf.write(variant);
								raf.writeInt(LAYOUT_EOF);
								raf.writeInt(0);
								return;
							case LAYOUT_TYPE:
								raf.write(variant);
								return;
							case LAYOUT_KEYS:
								if (version >= 2) {
									int count = raf.readInt();
									length = count * 21;
								}
							default:
								if (raf.skipBytes(length) != length) {
									break loop;
								}
								break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			raf.seek(0);
			raf.writeInt(LAYOUT_SIGNATURE);
			raf.writeInt(LAYOUT_VERSION);
			raf.writeInt(LAYOUT_TYPE);
			raf.writeInt(1);
			raf.write(variant);
			if (variant != TYPE_CUSTOM) {
				raf.writeInt(LAYOUT_EOF);
				raf.writeInt(0);
				raf.setLength(raf.getFilePointer());
				return;
			}
			raf.writeInt(LAYOUT_KEYS);
			raf.writeInt(keypad.length * 21 + 4);
			raf.writeInt(keypad.length);
			for (VirtualKey key : keypad) {
				raf.writeInt(key.hashCode());
				raf.writeBoolean(key.visible);
				raf.writeInt(key.snapOrigin);
				raf.writeInt(key.snapMode);
				PointF snapOffset = key.snapOffset;
				raf.writeFloat(snapOffset.x);
				raf.writeFloat(snapOffset.y);
			}
			raf.writeInt(LAYOUT_SCALES);
			raf.writeInt(keyScales.length * 4 + 4);
			raf.writeInt(keyScales.length);
			for (float keyScale : keyScales) {
				raf.writeFloat(keyScale);
			}
			raf.writeInt(LAYOUT_EOF);
			raf.writeInt(0);
			raf.setLength(raf.getFilePointer());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int readLayoutType() {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			int custom = 0;
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				switch (block) {
					case LAYOUT_EOF:
						return custom == 3 ? 0 : -1;
					case LAYOUT_TYPE:
						return dis.read();
					case LAYOUT_KEYS:
						if (version >= 2) {
							int count = dis.readInt();
							length = count * 21;
						}
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 1;
						break;
					case LAYOUT_SCALES:
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 2;
						break;
					default:
						if (dis.skipBytes(length) != length) {
							return -1;
						}
				}
			}
		} catch (FileNotFoundException e) {
			Log.w(TAG, "readLayoutType() threw an FileNotFoundException: " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	private void readLayout() throws IOException {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				int count;
				switch (block) {
					case LAYOUT_EOF:
						return;
					case LAYOUT_KEYS:
						count = dis.readInt();
						for (int i = 0; i < count; i++) {
							int hash = dis.readInt();
							boolean found = false;
							for (VirtualKey key : keypad) {
								if (key.hashCode() == hash) {
									if (version >= 2) {
										key.visible = dis.readBoolean();
									}
									key.snapOrigin = dis.readInt();
									key.snapMode = dis.readInt();
									key.snapOffset.x = dis.readFloat();
									key.snapOffset.y = dis.readFloat();
									found = true;
									break;
								}
							}
							if (!found) {
								dis.skipBytes(version >= 2 ? 17 : 16);
							}
						}
						break;
					case LAYOUT_SCALES:
						count = dis.readInt();
						if (version >= 3) {
							for (int i = 0; i < count; i++) {
								keyScales[i] = dis.readFloat();
							}
						} else if (count * 2 <= keyScales.length) {
							for (int i = 0, len = count * 2; i < len;) {
								float v = dis.readFloat();
								keyScales[i++] = v;
								keyScales[i++] = v;
							}
						} else {
							dis.skipBytes(count * 4);
						}
						break;
					default:
						dis.skipBytes(length);
						break;
				}
			}
		}
	}

	public String[] getKeyNames() {
		String[] names = new String[KEYBOARD_SIZE];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			names[i] = keypad[i].label;
		}
		return names;
	}

	public boolean[] getKeysVisibility() {
		boolean[] states = new boolean[KEYBOARD_SIZE];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			states[i] = !keypad[i].visible;
		}
		return states;
	}

	public void setKeysVisibility(boolean[] states) {
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			keypad[i].visible = !states[i];
		}
		overlayView.postInvalidate();
	}

	@Override
	public void setTarget(Canvas canvas) {
		target = canvas;
		analog.setTarget(canvas);
		dpad.setTarget(canvas);
		letterPad.setTarget(canvas);
		highlightGroup(-1);
	}

	/** O menu mudou o fast-forward (ativar/velocidade): aplica e redesenha. */
	public void apexFastForwardChanged() {
		ff.applySettings();
		apexRelayout();
	}

	/** Recalcula o layout (apos mudar tamanhos/ativar extras). */
	public void apexRelayout() {
		if (screen != null && overlayView != null) {
			resize(screen, virtualScreen.left, virtualScreen.top, virtualScreen.right, virtualScreen.bottom);
		}
	}

	private void setSnap(int key, int origin, int mode, boolean visible) {
		VirtualKey vKey = keypad[key];
		vKey.snapOrigin = origin;
		vKey.snapMode = mode;
		vKey.snapOffset.set(0, 0);
		vKey.snapValid = false;
		vKey.visible = visible;
	}

	private boolean findSnap(int target, int origin) {
		VirtualKey tk = keypad[target];
		VirtualKey ok = keypad[origin];
		tk.snapMode = RectSnap.getSnap(tk.rect, ok.rect, snapRadius, RectSnap.COARSE_MASK, true);
		if (tk.snapMode != RectSnap.NO_SNAP) {
			tk.snapOrigin = origin;
			tk.snapOffset.set(0, 0);
			for (int i = 0; i < keypad.length; i++) {
				origin = keypad[origin].snapOrigin;
				if (origin == SCREEN) {
					return true;
				}
			}
		}
		return false;
	}

	private void snapKey(int key, int level) {
		if (level >= snapStack.length) {
			Log.d(TAG, "Snap loop detected: ");
			for (int i = 1; i < snapStack.length; i++) {
				System.out.print(snapStack[i]);
				System.out.print(", ");
			}
			Log.d(TAG, String.valueOf(key));
			return;
		}
		snapStack[level] = key;
		VirtualKey vKey = keypad[key];
		if (vKey.snapOrigin == SCREEN) {
			RectSnap.snap(vKey.rect, screen, vKey.snapMode, vKey.snapOffset);
		} else {
			if (!keypad[vKey.snapOrigin].snapValid) {
				snapKey(vKey.snapOrigin, level + 1);
			}
			RectSnap.snap(vKey.rect, keypad[vKey.snapOrigin].rect,
					vKey.snapMode, vKey.snapOffset);
		}
		vKey.snapValid = true;
	}

	private void snapKeys() {
		obscuresVirtualScreen = false;
		boolean isPhone = isPhone();
		for (int i = 0; i < keypad.length; i++) {
			snapKey(i, 0);
			VirtualKey key = keypad[i];
			RectF rect = key.rect;
			key.corners = (int) (Math.min(rect.width(), rect.height()) * 0.25F);
			if (!isPhone && RectF.intersects(rect, virtualScreen)) {
				obscuresVirtualScreen = true;
				key.opaque = false;
			} else {
				key.opaque = true;
			}
		}
		boolean opaque = !obscuresVirtualScreen || settings.vkForceOpacity;
		for (VirtualKey key : keypad) {
			key.opaque &= opaque;
		}
	}

	public boolean isPhone() {
		return layoutVariant == TYPE_PHONE || layoutVariant == TYPE_PHONE_ARROWS;
	}

	private void highlightGroup(int group) {
		for (VirtualKey aKeypad : keypad) {
			aKeypad.selected = false;
		}
		if (group >= 0) {
			for (int key = 0; key < keyScaleGroups[group].length; key++) {
				keypad[keyScaleGroups[group][key]].selected = true;
			}
		}
	}

	private int abPressed = -1;
	private boolean abMoved;
	private float abDownX;
	private float abDownY;

	/** A = modo mover; B = modo tamanho. Toque de novo no mesmo botao salva; no outro, troca de modo. */
	private void abToggle(int key) {
		MicroActivity activity = ContextHolder.getActivity();
		if (activity != null) {
			activity.apexVkToggleEdit(key == KEY_A ? LAYOUT_KEYS : LAYOUT_SCALES);
		}
	}

	public int getLayoutEditMode() {
		return layoutEditMode;
	}

	public void setLayoutEditMode(int mode) {
		layoutEditMode = mode;
		editPointer = -1;
		barPointer = -1;
		if (mode == LAYOUT_KEYS) {
			detachAllKeys();
		}
		int group = -1;
		if (mode == LAYOUT_SCALES) {
			editedIndex = 0;
			group = 0;
		}
		highlightGroup(group);
		handler.removeCallbacks(this);
		visible = true;
		overlayView.postInvalidate();
		hide();
	}

	/** Solta todas as teclas umas das outras: cada uma passa a depender so da tela. */
	private void detachAllKeys() {
		if (screen == null) {
			return;
		}
		for (VirtualKey key : keypad) {
			key.snapMode = RectSnap.getSnap(key.rect, screen, key.snapOffset);
			key.snapOrigin = SCREEN;
		}
		snapKeys();
	}

	private void resizeKey(int key, float w, float h) {
		VirtualKey vKey = keypad[key];
		vKey.resize(w, h);
		vKey.snapValid = false;
	}

	private void resizeKeyGroup(int group) {
		float sizeX = keySize * keyScales[group * 2];
		float sizeY = keySize * keyScales[group * 2 + 1];
		for (int key = 0; key < keyScaleGroups[group].length; key++) {
			int idx = keyScaleGroups[group][key];
			if (idx == KEY_A || idx == KEY_B) {
				// A e B tem tamanho proprio (fixo, a menos que o usuario libere na janela do teclado)
				float s = ApexPrefs.abEditable() ? ApexPrefs.abScale() : 1f;
				resizeKey(idx, keySize * s, keySize * s);
			} else {
				resizeKey(idx, sizeX, sizeY);
			}
		}
	}

	@Override
	public void resize(RectF screen, float left, float top, float right, float bottom) {
		this.screen = screen;
		virtualScreen.set(left, top, right, bottom);
		snapRadius = keyScales[0];
		for (int i = 1; i < keyScales.length; i++) {
			if (keyScales[i] < snapRadius) {
				snapRadius = keyScales[i];
			}
		}

		float keySize = getKeySize(screen.width(), screen.height());
		snapRadius = keySize * snapRadius / 4;
		this.keySize = keySize;
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		analog.layout(screen);
		dpad.layout(screen);
		letterPad.layout(screen);
		ff.layout(screen);
		overlayView.postInvalidate();
		int delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			for (VirtualKey key : associatedKeys) {
				if (key != null) {
					return;
				}
			}
			handler.postDelayed(this, delay);
		}
	}

	private float getKeySize(float screenWidth, float screenHeight) {
		float min = screenWidth;
		float max = screenHeight;
		boolean landscape = min > max;
		if (min > max) {
			float tmp = max;
			max = min;
			min = tmp;
		}

		boolean nonWide = max / min < 2;
		float keySize;
		if (isPhone()) {
			keySize = min / 6.0F;
		} else if (nonWide || landscape) {
			keySize = max / 12F;
		} else {
			keySize = min / 6.5F;
		}
		if (!isPhone()) {
			keySize *= ApexPrefs.buttonScale();
		}
		return keySize;
	}

	@Override
	public void paint(CanvasWrapper g) {
		if (visible) {
			boolean grid = layoutEditMode == LAYOUT_KEYS && screen != null;
			if (grid && ApexPrefs.gridOn()) {
				paintGrid(g);
			}
			for (VirtualKey key : keypad) {
				if (key.visible) {
					key.paint(g);
				}
			}
			boolean opaque = layoutEditMode != LAYOUT_EOF;
			analog.paint(g, opaque);
			dpad.paint(g, opaque);
			letterPad.paint(g, opaque);
			ff.paint(g, opaque);
			if (grid) {
				paintGridBar(g);
			}
		}
	}

	/** Desenha a grade (linhas finas, a cada 5 casas mais fortes, cruz no centro). */
	private void paintGrid(CanvasWrapper g) {
		float cell = ApexPrefs.gridCell();
		// se a casa for muito pequena na tela, desenha so algumas linhas (o encaixe continua exato)
		int every = Math.max(1, (int) Math.ceil(7f / cell));
		float step = cell * every;
		int nx = (int) (screen.width() / step) + 1;
		int ny = (int) (screen.height() / step) + 1;
		int need = (nx + ny + 4) * 4;
		if (gridLines.length < need) {
			gridLines = new float[need];
		}
		g.setStrokeWidth(1f);
		// linhas finas
		int n = 0;
		for (int i = 0; i <= nx; i++) {
			float x = screen.left + i * step;
			if (x > screen.right) break;
			gridLines[n++] = x; gridLines[n++] = screen.top;
			gridLines[n++] = x; gridLines[n++] = screen.bottom;
		}
		for (int i = 0; i <= ny; i++) {
			float y = screen.top + i * step;
			if (y > screen.bottom) break;
			gridLines[n++] = screen.left; gridLines[n++] = y;
			gridLines[n++] = screen.right; gridLines[n++] = y;
		}
		g.setDrawColor(0x38FFFFFF);
		g.drawLines(gridLines, n);
		// cruz no centro da tela
		g.setStrokeWidth(2f);
		g.setDrawColor(0x90FFFFFF);
		float[] cross = {screen.centerX(), screen.top, screen.centerX(), screen.bottom,
				screen.left, screen.centerY(), screen.right, screen.centerY()};
		g.drawLines(cross, 8);
		g.setStrokeWidth(1f);
	}

	private void layoutGridBar() {
		float h = Math.max(keySize * 0.55f, 44f * ContextHolder.getAppContext()
				.getResources().getDisplayMetrics().density);
		float gap = h * 0.12f;
		float wGrid = h * 2.6f, wBtn = h, wVal = h * 1.2f;
		float total = wGrid + wBtn + wVal + wBtn + gap * 3;
		float left = screen.centerX() - total / 2f;
		float top = screen.top + gap;
		barGrid.set(left, top, left + wGrid, top + h);
		barMinus.set(barGrid.right + gap, top, barGrid.right + gap + wBtn, top + h);
		barValue.set(barMinus.right + gap, top, barMinus.right + gap + wVal, top + h);
		barPlus.set(barValue.right + gap, top, barValue.right + gap + wBtn, top + h);
	}

	private void paintBarButton(CanvasWrapper g, RectF r, String text, boolean active) {
		g.setFillColor(active ? 0xF0206A9E : 0xE0102030);
		g.setDrawColor(0xFFFFFFFF);
		g.setStrokeWidth(2f);
		g.fillRoundRect(r, 12, 12);
		g.drawRoundRect(r, 12, 12);
		g.setTextColor(0xFFFFFFFF);
		g.drawString(text, r.centerX(), r.centerY());
	}

	private void paintGridBar(CanvasWrapper g) {
		layoutGridBar();
		boolean on = ApexPrefs.gridOn();
		paintBarButton(g, barGrid, on ? "Grade: ON" : "Grade: OFF", on);
		paintBarButton(g, barMinus, "-", false);
		paintBarButton(g, barValue, String.valueOf(ApexPrefs.gridSize()), false);
		paintBarButton(g, barPlus, "+", false);
		g.setStrokeWidth(1f);
	}

	/** Toque na barra de grade. Devolve true se o toque foi dela. */
	private boolean barPress(float x, float y) {
		layoutGridBar();
		if (barGrid.contains(x, y)) {
			ApexPrefs.setGridOn(!ApexPrefs.gridOn());
		} else if (barMinus.contains(x, y)) {
			int v = ApexPrefs.gridSize();
			ApexPrefs.setGridSize(v > 16 ? v - 4 : v - 2);
		} else if (barPlus.contains(x, y)) {
			int v = ApexPrefs.gridSize();
			ApexPrefs.setGridSize(v >= 16 ? v + 4 : v + 2);
		} else if (!barValue.contains(x, y)) {
			return false;
		}
		overlayView.postInvalidate();
		return true;
	}

	@Override
	public boolean pointerPressed(int pointer, float x, float y) {
		if (layoutEditMode == LAYOUT_KEYS && screen != null) {
			if (barPointer < 0 && editPointer < 0 && barPress(x, y)) {
				barPointer = pointer;
				return false;
			}
			if (barPointer >= 0 || (editPointer >= 0 && editPointer != pointer)) {
				return false; // so um dedo por vez edita
			}
			editPointer = pointer;
		}
		if (layoutEditMode == LAYOUT_EOF) {
			if (ff.press(pointer, x, y) || letterPad.press(pointer, x, y) || analog.press(pointer, x, y) || dpad.press(pointer, x, y)) {
				overlayView.postInvalidate();
				return false;
			}
		} else if (layoutEditMode == LAYOUT_KEYS) {
			if (ff.editBegin(x, y) || letterPad.editBegin(x, y) || analog.editBegin(x, y) || dpad.editBegin(x, y)) {
				return false;
			}
		}
		if (layoutEditMode != LAYOUT_EOF) {
			abPressed = -1;
			int[] ab = {KEY_A, KEY_B};
			for (int k : ab) {
				if (keypad[k].contains(x, y)) {
					abPressed = k;
					abMoved = false;
					abDownX = x;
					abDownY = y;
					if (layoutEditMode == LAYOUT_KEYS && ApexPrefs.abEditable()) {
						editedIndex = k;
						RectF r = keypad[k].rect;
						offsetX = x - r.left;
						offsetY = y - r.top;
					} else if (layoutEditMode == LAYOUT_KEYS) {
						editedIndex = -1;
					}
					return false;
				}
			}
		}
		switch (layoutEditMode) {
			case LAYOUT_EOF:
				if (pointer > associatedKeys.length) {
					return false;
				}
				for (VirtualKey key : keypad) {
					if (key.contains(x, y)) {
						vibrate();
						associatedKeys[pointer] = key;
						key.onDown();
						overlayView.postInvalidate();
						break;
					}
				}
				break;
			case LAYOUT_KEYS:
				editedIndex = -1;
				for (int i = 0; i < keypad.length; i++) {
					if (keypad[i].contains(x, y)) {
						editedIndex = i;
						RectF rect = keypad[i].rect;
						offsetX = x - rect.left;
						offsetY = y - rect.top;
						break;
					}
				}
				break;
			case LAYOUT_SCALES:
				int index = -1;
				for (int group = 0; group < keyScaleGroups.length && index < 0; group++) {
					for (int key = 0; key < keyScaleGroups[group].length && index < 0; key++) {
						if (keypad[keyScaleGroups[group][key]].contains(x, y)) {
							index = group;
						}
					}
				}
				if (index >= 0) {
					editedIndex = index;
					highlightGroup(index);
					overlayView.postInvalidate();
				}
				offsetX = x;
				offsetY = y;
				prevScaleX = keyScales[editedIndex * 2];
				prevScaleY = keyScales[editedIndex * 2 + 1];
				break;
		}
		return false;
	}

	@Override
	public boolean pointerDragged(int pointer, float x, float y) {
		if (layoutEditMode == LAYOUT_KEYS && pointer != editPointer) {
			return false;
		}
		if (layoutEditMode == LAYOUT_EOF) {
			if (ff.drag(pointer, x, y) || letterPad.drag(pointer, x, y) || analog.drag(pointer, x, y) || dpad.drag(pointer, x, y)) {
				return false;
			}
		} else if (layoutEditMode == LAYOUT_KEYS) {
			if (ff.isEditing()) {
				ff.editDrag(x, y);
				return false;
			}
			if (analog.isEditing()) {
				analog.editDrag(x, y);
				return false;
			}
			if (dpad.isEditing()) {
				dpad.editDrag(x, y);
				return false;
			}
			if (letterPad.isEditing()) {
				letterPad.editDrag(x, y);
				return false;
			}
		}
		if (abPressed >= 0) {
			float slop = Math.min(screen.width(), screen.height()) * 0.03f;
			if (Math.abs(x - abDownX) > slop || Math.abs(y - abDownY) > slop) {
				abMoved = true;
			}
			if (layoutEditMode == LAYOUT_SCALES
					|| (layoutEditMode == LAYOUT_KEYS && !ApexPrefs.abEditable())) {
				return false;
			}
		}
		switch (layoutEditMode) {
			case LAYOUT_EOF:
				if (pointer > associatedKeys.length) {
					return false;
				}
				VirtualKey aKey = associatedKeys[pointer];
				if (aKey == null) {
					pointerPressed(pointer, x, y);
				} else if (!aKey.contains(x, y)) {
					associatedKeys[pointer] = null;
					aKey.onUp();
					overlayView.postInvalidate();
					pointerPressed(pointer, x, y);
				}
				break;
			case LAYOUT_KEYS:
				if (editedIndex >= 0) {
					VirtualKey key = keypad[editedIndex];
					RectF rect = key.rect;
					float w = rect.width();
					float h = rect.height();
					float nx = x - offsetX;
					float ny = y - offsetY;
					boolean grid = ApexPrefs.gridOn();
					if (grid) {
						// o centro do botao encaixa nas linhas da grade
						nx = ApexPrefs.gridSnap(nx + w / 2f, screen.left) - w / 2f;
						ny = ApexPrefs.gridSnap(ny + h / 2f, screen.top) - h / 2f;
					}
					nx = Math.max(screen.left, Math.min(screen.right - w, nx));
					ny = Math.max(screen.top, Math.min(screen.bottom - h, ny));
					rect.offsetTo(nx, ny);
					// move SO este botao: ele depende apenas da tela, nunca de outro botao
					key.snapOrigin = SCREEN;
					key.snapMode = RectSnap.getSnap(rect, screen, key.snapOffset);
					if (!grid) {
						if (Math.abs(key.snapOffset.x) <= snapRadius) {
							key.snapOffset.x = 0;
						}
						if (Math.abs(key.snapOffset.y) <= snapRadius) {
							key.snapOffset.y = 0;
						}
					}
					snapKey(editedIndex, 0);
					overlayView.postInvalidate();
				}
				break;
			case LAYOUT_SCALES:
				float dx = x - offsetX;
				float dy = offsetY - y;
				float scale;
				int index = this.editedIndex * 2;
				if (Math.abs(dx) > Math.abs(dy)) {
					scale = prevScaleX + dx / Math.min(screen.centerX(), screen.centerY());
				} else {
					scale = prevScaleY + dy / Math.min(screen.centerX(), screen.centerY());
					index++;
				}
				if (Math.abs(1 - scale) <= SCALE_SNAP_RADIUS) {
					scale = 1;
				} else {
					for (int i = index % 2; i < keyScales.length; i += 2) {
						if (i != index && Math.abs(keyScales[i] - scale) <= SCALE_SNAP_RADIUS) {
							scale = keyScales[i];
							break;
						}
					}
				}
				keyScales[index] = scale;
				resizeKeyGroup(this.editedIndex);
				snapKeys();
				overlayView.postInvalidate();
				break;
		}
		return false;
	}

	@Override
	public boolean pointerReleased(int pointer, float x, float y) {
		if (layoutEditMode == LAYOUT_KEYS) {
			if (pointer == barPointer) {
				barPointer = -1;
				return false;
			}
			if (pointer != editPointer) {
				return false;
			}
			editPointer = -1;
		}
		return releaseImpl(pointer, x, y);
	}

	private boolean releaseImpl(int pointer, float x, float y) {
		if (ff.isEditing() || analog.isEditing() || letterPad.isEditing() || dpad.isEditing()) {
			if (ff.isEditing()) ff.editEnd();
			if (dpad.isEditing()) dpad.editEnd();
			if (analog.isEditing()) analog.editEnd();
			if (letterPad.isEditing()) letterPad.editEnd();
			return false;
		}
		if (abPressed >= 0) {
			int k = abPressed;
			boolean movable = layoutEditMode == LAYOUT_KEYS && ApexPrefs.abEditable();
			if (!abMoved || !movable) {
				abPressed = -1;
				if (!abMoved) {
					abToggle(k);
				}
				return false;
			}
			abPressed = -1;
		}
		if (layoutEditMode == LAYOUT_EOF) {
			letterPad.release(pointer);
			ff.release(pointer, x, y);
			analog.release(pointer);
			dpad.release(pointer);
			if (pointer > associatedKeys.length) {
				return false;
			}
			VirtualKey key = associatedKeys[pointer];
			if (key != null) {
				associatedKeys[pointer] = null;
				key.onUp();
				if (key instanceof EditKey && key.contains(x, y)) {
					abToggle(key == keypad[KEY_A] ? KEY_A : KEY_B);
				}
				overlayView.postInvalidate();
			}
		} else if (layoutEditMode == LAYOUT_KEYS) {
			snapKeys();
			editedIndex = -1;
		}
		return false;
	}

	@Override
	public void show() {
		if (settings.vkHideDelay > 0 && obscuresVirtualScreen) {
			handler.removeCallbacks(this);
			if (!visible) {
				visible = true;
				overlayView.postInvalidate();
			}
		}
	}

	@Override
	public void hide() {
		long delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			handler.postDelayed(this, delay);
		}
	}

	@Override
	public void cancel() {
		analog.cancel();
		dpad.cancel();
		letterPad.cancel();
		ff.cancel();
		for (VirtualKey key : keypad) {
			key.selected = false;
			handler.removeCallbacks(key);
		}
	}

	@Override
	public void run() {
		visible = false;
		overlayView.postInvalidate();
	}

	@Override
	public boolean keyPressed(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = true;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	@Override
	public boolean keyRepeated(int keyCode) {
		return false;
	}

	@Override
	public boolean keyReleased(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = false;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	private void vibrate() {
		if (settings.vkFeedback) ContextHolder.vibrateKey(FEEDBACK_DURATION);
	}

	public void setView(View view) {
		overlayView = view;
		analog.setView(view);
		dpad.setView(view);
		letterPad.setView(view);
		ff.setView(view);
	}

	public int getKeyStatesVodafone() {
		int keyStates = 0;
		for (int i = 0; i < keypad.length; i++) {
			VirtualKey key = keypad[i];
			if (key.selected) {
				keyStates |= getKeyBit(i);
			}
		}
		return keyStates;
	}

	private int getKeyBit(int vKey) {
		switch (vKey) {
			case KEY_NUM0      : return 1;       // 0 0 key         KEY_NUM0       = 9;
			case KEY_NUM1      : return 1 <<  1; // 1 1 key         KEY_NUM1       = 0;
			case KEY_NUM2      : return 1 <<  2; // 2 2 key         KEY_NUM2       = 1;
			case KEY_NUM3      : return 1 <<  3; // 3 3 key         KEY_NUM3       = 2;
			case KEY_NUM4      : return 1 <<  4; // 4 4 key         KEY_NUM4       = 3;
			case KEY_NUM5      : return 1 <<  5; // 5 5 key         KEY_NUM5       = 4;
			case KEY_NUM6      : return 1 <<  6; // 6 6 key         KEY_NUM6       = 5;
			case KEY_NUM7      : return 1 <<  7; // 7 7 key         KEY_NUM7       = 6;
			case KEY_NUM8      : return 1 <<  8; // 8 8 key         KEY_NUM8       = 7;
			case KEY_NUM9      : return 1 <<  9; // 9 9 key         KEY_NUM9       = 8;
			case KEY_STAR      : return 1 << 10; // 10 * key        KEY_STAR       = 10;
			case KEY_POUND     : return 1 << 11; // 11 # key        KEY_POUND      = 11;
			case KEY_UP        : return 1 << 12; // 12 Up key       KEY_UP         = 17;
			case KEY_LEFT      : return 1 << 13; // 13 Left key     KEY_LEFT       = 19;
			case KEY_RIGHT     : return 1 << 14; // 14 Right key    KEY_RIGHT      = 20;
			case KEY_DOWN      : return 1 << 15; // 15 Down key     KEY_DOWN       = 22;
			case KEY_FIRE      : return 1 << 16; // 16 Select key   KEY_FIRE       = 24;
			case KEY_SOFT_LEFT : return 1 << 17; // 17 Softkey 1    KEY_SOFT_LEFT  = 12;
			case KEY_SOFT_RIGHT: return 1 << 18; // 18 Softkey 2    KEY_SOFT_RIGHT = 13;
			// TODO: 05.08.2020 Softkey3 mapped to KEY_C
			case KEY_C         : return 1 << 19; // 19 Softkey 3    KEY_C          = 15;
			case KEY_UP_RIGHT  : return 1 << 20; // 20 Upper Right  KEY_UP_RIGHT   = 18;
			case KEY_UP_LEFT   : return 1 << 21; // 21 Upper Left   KEY_UP_LEFT    = 16;
			case KEY_DOWN_RIGHT: return 1 << 22; // 22 Lower Right  KEY_DOWN_RIGHT = 23;
			case KEY_DOWN_LEFT : return 1 << 23; // 23 Lower Left   KEY_DOWN_LEFT  = 21;
		}
		return 0;
	}

	public void saveScreenParams() {
		float scale = virtualScreen.width() / screen.width();
		settings.screenScaleRatio = Math.round(scale * 100);
		settings.screenGravity = 1;
		Canvas.setScale(settings.screenGravity, settings.screenScaleType, settings.screenScaleRatio);
		ProfilesManager.saveConfig(settings);
	}

	private class VirtualKey implements Runnable {
		final String label;
		final int keyCode;
		String imageId;
		final RectF rect = new RectF();
		final PointF snapOffset = new PointF();
		int snapOrigin;
		int snapMode;
		boolean snapValid;
		boolean selected;
		boolean visible = true;
		boolean arrow;
		boolean opaque = true;
		int corners;
		private final int hashCode;
		private int repeatCount;
		private int sentCode;

		VirtualKey(int keyCode, String label) {
			this.keyCode = keyCode;
			this.label = label;
			hashCode = 31 * (31 + this.keyCode);
		}

		void resize(float width, float height) {
			rect.right = rect.left + width;
			rect.bottom = rect.top + height;
		}

		boolean contains(float x, float y) {
			return visible && rect.contains(x, y);
		}

		/** Rotulo mostrado: se o numero estiver mapeado, mostra a letra. */
		String displayLabel() {
			int m = ApexPrefs.remapNumber(keyCode);
			return m != keyCode ? String.valueOf(Character.toUpperCase((char) m)) : label;
		}

		void paint(CanvasWrapper g) {
			String shown = displayLabel();
			int bgColor;
			int fgColor;
			if (selected) {
				bgColor = settings.vkBgColorSelected;
				fgColor = settings.vkFgColorSelected;
			} else {
				bgColor = settings.vkBgColor;
				fgColor = settings.vkFgColor;
			}
			int alpha = (opaque || layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha) << 24;
			Bitmap png = ApexPrefs.buttonBitmap(imageId, selected);
			if (png != null) {
				// normal: levemente transparente; pressionado: cor normal, sem transparencia
				// com imagem: so a imagem, sem a letra da tecla por cima
				g.drawBitmap(png, rect, Math.round(255 * (selected ? ApexPrefs.pressedOpacity() : ApexPrefs.normalOpacity())));
				return;
			}
			g.setFillColor(alpha | bgColor);
			g.setTextColor(alpha | fgColor);
			g.setDrawColor(alpha | settings.vkOutlineColor);

			switch (settings.vkButtonShape) {
				case ROUND_RECT_SHAPE:
					g.fillRoundRect(rect, corners, corners);
					g.drawRoundRect(rect, corners, corners);
					break;
				case RECT_SHAPE:
					g.fillRect(rect);
					g.drawRect(rect);
					break;
				case OVAL_SHAPE:
					g.fillArc(rect, 0, 360);
					g.drawArc(rect, 0, 360);
					break;
			}
			g.drawString(shown, rect.centerX(), rect.centerY());
		}

		@NonNull
		public String toString() {
			return "[" + label + ": " + rect.left + ", " + rect.top + ", " + rect.right + ", " + rect.bottom + "]";
		}

		public int hashCode() {
			return hashCode;
		}

		@Override
		public void run() {
			if (target == null) {
				selected = false;
				repeatCount = 0;
				return;
			}
			if (selected) {
				onRepeat();
			} else {
				repeatCount = 0;
			}
		}

		public void onRepeat() {
			handler.postDelayed(this, repeatCount > 6 ? 80 : REPEAT_INTERVALS[repeatCount++]);
			target.postKeyRepeated(ApexPrefs.remapNumber(keyCode));
		}

		protected void onDown() {
			selected = true;
			sentCode = ApexPrefs.remapNumber(keyCode);
			target.postKeyPressed(sentCode);
			handler.postDelayed(this, 400);
		}

		public void onUp() {
			selected = false;
			handler.removeCallbacks(this);
			target.postKeyReleased(sentCode != 0 ? sentCode : keyCode);
			sentCode = 0;
		}
	}

	/** Botoes A e B (Sony): nao enviam tecla ao jogo, abrem os modos de edicao do teclado. */
	private class EditKey extends VirtualKey {
		EditKey(int keyCode, String label) {
			super(keyCode, label);
		}

		@Override
		protected void onDown() {
			selected = true;
		}

		@Override
		public void onUp() {
			selected = false;
		}
	}

	private class DualKey extends VirtualKey {

		final int secondKeyCode;
		private final int hashCode;

		DualKey(int keyCode, int secondKeyCode, String label) {
			super(keyCode, label);
			if (secondKeyCode == 0) throw new IllegalArgumentException();
			this.secondKeyCode = secondKeyCode;
			hashCode = 31 * (31 + this.keyCode) + this.secondKeyCode;
		}

		@Override
		protected void onDown() {
			super.onDown();
			target.postKeyPressed(secondKeyCode);
		}

		@Override
		public void onUp() {
			super.onUp();
			target.postKeyReleased(secondKeyCode);
		}

		@Override
		public void onRepeat() {
			super.onRepeat();
			target.postKeyRepeated(secondKeyCode);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}
	}

	private class MenuKey extends VirtualKey {

		MenuKey() {
			super(KeyMapper.KEY_OPTIONS_MENU, "M");
		}

		@Override
		protected void onDown() {
			selected = true;
			handler.postDelayed(this, 500);
		}

		@Override
		public void onUp() {
			if (selected) {
				selected = false;
				handler.removeCallbacks(this);
				MicroActivity activity = ContextHolder.getActivity();
				if (activity != null) {
					activity.openOptionsMenu();
				}
			}
		}

		@Override
		public void run() {
			selected = false;
			MicroActivity activity = ContextHolder.getActivity();
			if (activity != null) {
				activity.runOnUiThread(activity::showExitConfirmation);
			}
		}
	}
}
