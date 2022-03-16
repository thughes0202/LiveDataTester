package livedatatester;

import com.ib.client.Contract;
import com.ib.client.TickType;
import com.ib.client.Types;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.TopMktDataAdapter;
import com.ib.controller.Bar;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;

public class LiveDataNT extends TopMktDataAdapter implements ApiController.IRealTimeBarHandler {
    
    private Contract mContract;    
    private final static Logger mLogger = Logger.getLogger("LiveDataTester");
    
    //streaming
    private Timer mTimer30;
    private double mOpen;
    private double mHigh;
    private double mLow;
    private double mClose;
    private long mVolume;
    private long mBarTime;
    
    //realtime bars
    private double mOpenRTB;
    private double mHighRTB;
    private double mLowRTB;
    private double mCloseRTB;
    private long mVolumeRTB;
    private long mBarTimeRTB;    
    private boolean mFirst5SecBar = true;
    private boolean mReplaceOpen = false;
    private long m30SecTime;
    private double mLastCloseRTB;
    
    //listeners
    private ActionListener mStreamBarListener;
    private ActionListener mRTBarListener;
    private ActionListener mPriceListener = null;    
    
    public LiveDataNT(Contract contract) {
        mContract = contract;
        AHcontroller.start(new AHcontroller(), 0);
        AHcontroller.INSTANCE.controller().client().reqMarketDataType(1);
        mLogger.info("setting market date type to live");

        startTimer();
        startLiveData();
    }
    
    public void setStreamingListener(ActionListener listener) {
        mStreamBarListener = listener;
    }
    
    public void setBarListener(ActionListener listener) {
        mRTBarListener = listener;        
    }
    
    public void setPriceListener(ActionListener listener) {
        mPriceListener = listener;
    }

    private void startLiveData() {
        AHcontroller.INSTANCE.controller().reqTopMktData(mContract, "", false, this);
        mLogger.info("starting live streaming data for " + mContract.symbol());
        
        AHcontroller.INSTANCE.miscDelay(500);
        AHcontroller.INSTANCE.controller().reqRealTimeBars(mContract, Types.WhatToShow.TRADES, false, this);        
        mLogger.info("starting realtime bars for " + mContract.symbol());    
        
        //set initial collection values
        mOpen = 0;  
        mHigh = 0;
        mLow = 999999;
        mClose = 0;
        mVolume = 0;
        mBarTime = 0;
    }
    
    //sometimes IB stops sending bars, this restarts them
    public void restartBars() {
        AHcontroller.INSTANCE.controller().cancelRealtimeBars(this);
        AHcontroller.INSTANCE.miscDelay(500);
        AHcontroller.INSTANCE.controller().reqRealTimeBars(mContract, Types.WhatToShow.TRADES, false, this);        
    }

    public void cancelLiveData() {        
        AHcontroller.INSTANCE.controller().cancelTopMktData(this);              
        AHcontroller.INSTANCE.controller().cancelRealtimeBars(this);
    }		
    
    private void startTimer() {
    
        //create the 30sec timer for creating bars
        mTimer30 = new Timer();
        
        //calculate msec delay to next 30 sec boundary
        int waitMilliseconds;
        int seconds = LocalTime.now().getSecond();
        int milliseconds = LocalTime.now().getNano() / 1000000;
        if (seconds >= 30) {
            waitMilliseconds = ((59 - seconds) * 1000) + (1000 - milliseconds);
        } else {
            waitMilliseconds = ((29 - seconds) * 1000) + (1000 - milliseconds);
        }
        
        mLogger.info("initializing stream timer");
        mTimer30.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mLogger.info("in streaming timer");    

                //create and send the streaming bar
                Bar bar = new Bar(mBarTime, mHigh, mLow, mOpen, mClose, 0, mVolume, 0);    
                sendStreamBar(bar);
                
                //init for next collection 
                mBarTime = new GregorianCalendar().getTimeInMillis() / 1000;                
                mOpen = mClose;
                mHigh = mOpen;
                mLow = mOpen;
                mClose = mOpen;
                mVolume = 0;
                mLogger.info("out streaming timer");    
            }                
        }, waitMilliseconds, 30000);                                                                        
        
        mLogger.info("starttimer seconds: " + seconds);
        mLogger.info("starttimer msec: " + milliseconds);
        mLogger.info("starttimer msec wait: " + waitMilliseconds);        
    }
    
    private void newPrice(double price) {
        
        //set open, if not already set
        if (mOpen == 0) {
            mOpen = price;
        }
        
        //update high/low
        mHigh = price > mHigh ? price : mHigh;
        mLow = price < mLow ? price : mLow;        
        
        //pending close price is always last price
        mClose = price;
        
        //volume?
    }

    private void sendStreamBar(Bar bar) {      

        if (mStreamBarListener != null) {
            mLogger.info("sending bar to stream listener (for checking transit time...)");            
            try {
                if (bar.open() != 0 && bar.time() != 0) {
                    ActionEvent event = new ActionEvent(bar, 0, "");                
                    mStreamBarListener.actionPerformed(event);                        
                }

            } catch (Exception ex) {
                mLogger.info("exception caught in AutoTrader in LiveData thread:" + ex.getMessage());
            }
        }
    }
    
    private void sendRTBar(Bar bar) {      

        if (mRTBarListener != null) {
            try {
                ActionEvent event = new ActionEvent(bar, 0, "");                
                mRTBarListener.actionPerformed(event);                        

            } catch (Exception ex) {
                mLogger.info("exception caught in AutoTrader in LiveData thread:" + ex.getMessage());
            }
        }
    }    

    public String ticker() {
        return mContract.symbol();
    }

    private void sendLastPriceToListener(double price) {
        if (mPriceListener != null) {
            try {
                ActionEvent event = new ActionEvent(price, 0, "");                
                mPriceListener.actionPerformed(event);                        

            } catch (Exception ex) {
                mLogger.info("exception caught in AutoTrader in LiveData thread:" + ex.getMessage());
            }
        }
    }

    public double realtimePrice() {
        return mClose;  //mclose always has the last streaming price
    }

    @Override 
    public void tickPrice( TickType tickType, double price, int canAutoExecute) {
        switch( tickType) {
            case LAST: case DELAYED_LAST:
                newPrice(price);
                mLogger.info(String.format("tickType: %s, value: %f", tickType.field(), price));                        
                break;
            case OPEN: case DELAYED_OPEN:
//                m_todayOpen = price;
                mLogger.info("Just received tickType for today's session open value:");
                mLogger.info(String.format("tickType: %s, value: %f", tickType.field(), price));                        
            default: break;	
        }
    }

    @Override 
    public void tickSize( TickType tickType, int size) {
        switch( tickType) {                            
            case VOLUME: case DELAYED_VOLUME:
//                m_volume = size;
                break;
            default: break; 
        }
    }

    @Override 
    public void tickSnapshotEnd() {
    }

    @Override 
    public void tickString(TickType tickType, String value) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        switch( tickType) {
            case LAST_TIMESTAMP:                        
//                m_logger.info("raw timestamp: " + dtf.format(AHutil.barLocalTime(Long.parseLong(value))));

                //m_lastTime = Long.parseLong( value) * 1000;
//                m_logger.info(AHutil.threadId() + String.format("tickType: %s, value: %s", tickType.field(), 
//                        AHutil.UnixMillisecondsToString(m_lastTime, "yyyy-MM-dd HH:mm:ss:SSS")));
                break;
            default: break; 
        }
    }

    @Override 
    public void realtimeBar(Bar bar5) {
        mLogger.info("5sec bar: " + AHutil.csvBarLine(bar5));
        LocalTime time = AHutil.barLocalTime(bar5.time());                
        int second = time.getSecond();

        //if we're building the first ever record, skip bar until
        //we get a starting time (either xx:00 or xx:30)
        if (mFirst5SecBar) {
            if (second == 0 || second == 30) {
                mFirst5SecBar = false; 
                mReplaceOpen = false;
            } else {
                return;
            }
        }

        //copy the bar before processing it...in case we get another event
        Bar bar = new Bar(bar5.time(), bar5.high(), bar5.low(), bar5.open(), bar5.close(), 0, bar5.volume(), 0);  
        switch (second) {
            case 0: case 30:
                init30SecBar(bar);
                break;
            case 25: case 55:
                update30SecBar(bar);
                finalize30SecBar(bar);
                break;
            default : 
                update30SecBar(bar);
        }
        mLogger.info("exiting realtimeBar");                
    }

    private void init30SecBar(Bar bar5) {

        mLogger.info("initializing 30sec bar from 5sec bar");                

        m30SecTime = AHutil.barTimeLong(AHutil.barLocalDateTime(bar5.time()));

        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm:ss");                
        mLogger.info("actual first bar timestamp: " + tf.format(AHutil.barLocalTime(bar5.time())));
        mLogger.info("timestamp for 30sec record to be created: " + tf.format(AHutil.barLocalTime(m30SecTime)));

        mBarTimeRTB = bar5.time();
        mOpenRTB = bar5.open();
        mHighRTB = bar5.high();
        mLowRTB = bar5.low();
        mCloseRTB = bar5.close();

        //if this open is the same as the previous close, set flag
        //to replace the open value with the first different open
        //mReplaceOpen = mOpenRTB == mLastCloseRTB;

        //watch out for zero low. if found, set to arbitrarily high value
        if (mLowRTB == 0) {
            mLowRTB = 99999;
        }

        mLogger.info("exiting init30SecBar");                
    }

    private void finalize30SecBar(Bar bar5) {

        mLogger.info("finalizing 30sec bar from 5sec bar");

        //save the last close value for future comparison
        mLastCloseRTB = bar5.close();                

        //the close price here is what will show up in IB's 30sec history
        mCloseRTB = bar5.close();        
        
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                mLogger.info("in RT timer");    
                //create and send the streaming bar
                Bar rtBar = new Bar(mBarTimeRTB, mHighRTB, mLowRTB, mOpenRTB, mCloseRTB, 0, mVolumeRTB, 0);    
                mLogger.info("final 30sec RTB bar: " + AHutil.csvBarLine(rtBar));
                mLogger.info("sending bar to RTB listener (for checking transit time...)");                            
                sendRTBar(rtBar);
                
                //init for next collection 
                mBarTimeRTB = new GregorianCalendar().getTimeInMillis() / 1000;                
                mOpenRTB = mCloseRTB;
                mHighRTB = mOpenRTB;
                mLowRTB = mOpenRTB;
                mCloseRTB = mOpenRTB;
                mVolume = 0;
                mLogger.info("out RT timer");    
                
            }
        }, 1);

        mLogger.info("exiting finalize30SecBar");                
    }

    private void update30SecBar(Bar bar5) {

        mLogger.info("updating 30sec bar values from 5sec bar");

        //replace original Open price if triggered
        if (mReplaceOpen) {
            if (bar5.open() != mOpenRTB) {
                mLogger.info("replacing Open of " + String.valueOf(mOpenRTB) + " with " + String.valueOf(bar5.open()));
                mOpenRTB = bar5.open();

                //if we replaced the open, limit high/low to that new price
                //are within the bounds of the new open
                if (mLowRTB < mOpenRTB) {
                    mLowRTB = mOpenRTB;                            
                }
                if (mHighRTB > mOpenRTB) {
                    mHighRTB = mOpenRTB;
                }

                //only replaced once, on the first difference open price
                mReplaceOpen = false;
            }
        }

        //update high/low
        if (bar5.high() > mHighRTB) {
            mHighRTB = bar5.high();
            mLogger.info("new bar5 high: " + String.valueOf(mHighRTB));
        } else if (bar5.low() < mLowRTB) {
            mLowRTB = bar5.low();                
            mLogger.info("new bar5 low: " + String.valueOf(mLowRTB));
        }
        
        mLogger.info("exiting update30SecBar");
    }            

}
