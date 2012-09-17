//
//   Copyright 2012 Dave Bacon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package net.openracer.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoypadView extends View {
	private final Paint paint = new Paint();
	private final Paint paintBgDisabled = new Paint();
	private final Paint paintBgActive = new Paint();
	private final Paint paintBgInactive = new Paint();
	
	public JoypadView(Context context) {
		super(context);
		setup();
	}
	
	public JoypadView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup();
	}
	
	public JoypadView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup();
	}
	
	private void setup() {
		paint.setColor(Color.BLACK);
		paint.setStrokeWidth(0); // device-specific hairline only.
		paintBgDisabled.setColor(Color.WHITE);
		paintBgActive.setColor(Color.LTGRAY);
		paintBgInactive.setColor(Color.GRAY);
	}
	
	private Listener listener = new Listener() {
		@Override public void onActive(float x, float y, float pressure) { }
		@Override public void onInactive(float x, float y, float pressure) { }
	};
	
	private float y = 0.f;
	private float x = 0.f;
	private float pressure = 0.f;
	private boolean active = false;
	private RectF rect = new RectF();
	
	@Override
	protected void onDraw(Canvas canvas) {
		
		if (isEnabled()) {
			canvas.drawPaint(active ? paintBgActive : paintBgInactive);
			
			int w = getWidth();
			int h = getHeight();
			
			canvas.drawLine(    0,     y, 100,   y, paint);
			canvas.drawLine(    x,     0,   x, 100, paint);
			canvas.drawLine(w-100,     y,   w,   y, paint);
			canvas.drawLine(    x, h-100,   x,   h, paint);
			
			rect.set(0.f,(float)h-pressure*200, 50.f, (float)h);
			canvas.drawRect(rect, paint);
		} else {
			canvas.drawPaint(paintBgDisabled);
		}
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	public static interface Listener {

		void onActive(float x, float y, float pressure);

		void onInactive(float x, float y, float pressure);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		x = event.getX();
		y = event.getY();
		pressure = event.getPressure();
		
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			active = true;
		} else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
			active = true;
		} else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
			active = false;
			pressure = 0;
		}
		
		invalidate();
		
		if (active) {
			listener.onActive((float)x / getWidth(), (float)y / getHeight(), pressure);
		} else {
			listener.onInactive((float)x / getWidth(), (float)y / getHeight(), pressure);
		}
		
		return true;
	}
	
}
