package livedatatester;

import java.util.ArrayList;
import java.awt.event.ActionListener;
import com.ib.client.Contract;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IConnectionHandler;
import javax.swing.JOptionPane;

public class AHcontroller implements IConnectionHandler {
    //just one AHcontroller
    public static AHcontroller INSTANCE;

    //IB manager objects
//    private LiveData mLiveData;
//    private HistoryCollector mHistoryCollector = new HistoryCollector();
    
    //IB ApiController and its required loggers. The Loggers are implemented
    //below and simply reroute to the AH_Trader logger
    private ApiController mController;
    private final Logger mInLogger = new Logger();
    private final Logger mOutLogger = new Logger();
    private java.util.logging.Logger mLogger;
    
    //flag for historical-data-fetch in progress
    public boolean mHistoricalReqInProgress = false;        
    private boolean mTWSconnected = false;
   
    public AHcontroller() {
        mLogger = java.util.logging.Logger.getLogger("AHTrader");     
    }
    
//    public LiveData liveData() {
//        return mLiveData;
//    }
    
    public static void start( AHcontroller ah_controller, int clientId) {
        INSTANCE = ah_controller;
        INSTANCE.connect(clientId);
    }
    
    public void miscDelay(long Delay) {
        try {
            Thread.sleep(Delay);
        } catch (InterruptedException ex){            
        }
    }

    public void createLiveData() {
//        if (liveData() != null) {
//            return;
//        }
//        
//        //create LiveData data handler to request and receive top market data
//        mBaseWorker = baseWorker;
//        mLiveData = new LiveData(mBaseWorker);
    }
    
    @Override
    public void connected() {
        mTWSconnected = true;
        show( "connected"); 
        mLogger.info("AHcontroller connected to TWS");
    }
    
    public boolean TWSconnected() {
        return mTWSconnected;
    }
    
    @Override
    public void disconnected(){
        mTWSconnected = false;
        show( "disconnected");         
        mLogger.info("AHcontroller disconnected from TWS");
    }
    
    @Override
    public void accountList(ArrayList<String> list){
        
    }
    
    @Override
    public void error(Exception e){
        show(e.toString());        
    }
    
    @Override
    public void message(int id, int errorCode, String errorMsg){
        //some messages indicate events
        if (errorCode == 162) {
            mHistoricalReqInProgress = false;
        }        
        show( id + " " + errorCode + " " + errorMsg);    
    }
           
    @Override
    public void show(String msg) {
        if (msg.length() > 0) {        
            mLogger.info("[IB API (show)] - " + msg);
        }
        
        //critical errors - show and log no matter what 
        if (msg.toLowerCase().contains("requested market data is not subscribed")) {
            mLogger.info("Critical error: failed to start live data stream: " + msg);
            JOptionPane.showMessageDialog(null, "Critical error trying to start the live data stream.\n" +             
                                          "Error message: " + msg);
        }
    }
    
    public ApiController controller() {
        if ( mController == null ) {
            mController = new ApiController( (IConnectionHandler) this, mInLogger, mOutLogger );
        }
        return mController;
    }
        
    public void connect(int clientId) {
        controller().connect( "127.0.0.1", 7496, clientId, "" );     
    }
    
    public void disconnect() {
        controller().disconnect();
    }
    
    private class Logger implements ILogger {
        private java.util.logging.Logger m_logger;
        
        @Override
	public void log(String valueOf) {
        };
    }    
}
