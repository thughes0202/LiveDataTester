package livedatatester;

import com.ib.controller.Bar;
import com.ib.controller.Formats;
import java.util.logging.Logger;
import java.util.Calendar;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import javax.swing.JOptionPane;

public class AHutil {
    
    public static final long TWS_JAVA_EPOCH_SECONDS = 2208902400L;
    
    public static boolean isTWSRunning() {
        String line;
        String pidInfo = "";
        try {
            Process p = Runtime.getRuntime().exec(System.getenv("windir") +"\\system32\\"+"tasklist.exe");
            BufferedReader input =  new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                pidInfo += line.toLowerCase(); 
            }
            input.close();
        } catch (IOException ex) {  
            ex.printStackTrace();
            Logger.getLogger("AHTrader").info("unable to get TWS.EXE status due to exception:" + ex.getMessage());
            //Could be an innocuous error so don't block AHTrader from running. 
            return true;
        }
        
        boolean foundIt = pidInfo.contains("tws.exe");
        if (foundIt) {
            return true;
        } else {
            //we didn't find raw tws.exe, check for tws.exe launched by IBController
            try {
                //assumes that the jdk folder is on env path
                Process p = Runtime.getRuntime().exec("jps.exe");
                pidInfo = "";
                BufferedReader input =  new BufferedReader(new InputStreamReader(p.getInputStream()));
                while ((line = input.readLine()) != null) {
                    pidInfo += line.toLowerCase(); 
                }
                input.close();
            } catch (Exception ex) {
                Logger.getLogger("AHTrader").info("unable to find IBController due to exception:" + ex.getMessage());
                return true;
            }
        }
        return pidInfo.contains("ibcontroller");
    }
    
    public static void showInfoMsg(String msg) {    
        JOptionPane.showMessageDialog(null, msg);
    }
    
    public static void notImplemented(String msg) {
        showInfoMsg("Feature not implemented: " + msg);
    }
    
    public synchronized static String threadId() {
        return "[" + String.valueOf(Thread.currentThread().getId()) + "] ";
    }
    
    //if a log file already exists, copy it to LogArchive with a new name
    public static void archiveLogFile(String logFileName) {
        File file = new File(logFileName);
        String name = file.getName();
        String namePart = name.split("\\.")[0];
        String archiveName;
        
        if (file.isFile()) {
            try {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm-");            
                archiveName = "Log\\LogArchive\\" + dtf.format(LocalDateTime.now()) + namePart + ".log";
                Files.move(Paths.get(logFileName), 
                           Paths.get(archiveName), 
                           StandardCopyOption.REPLACE_EXISTING);                
            } catch (IOException ex) {
                ex.printStackTrace();                        
            }
        }
    }

    public static synchronized double stdDevIndexed(double[] inDouble, int start, int back) {
        
        //calc mean 
        double total = 0;
        for(int i = start; i > start-back; i--){
           total += inDouble[i]; 
        }
        double mean = total / back;    
        
        //calc variance
        double variance = 0.0;
        for(int i = start; i > start-back; i--){
        //   variance += Math.pow(inDouble[i] - mean, 2); 
           variance += (inDouble[i]-mean) * (inDouble[i]-mean);
        }
        
        //return stddev
        return  Math.sqrt(variance / back);
    }
    
    public static synchronized double round(double value) {
        int temp = (int) ((value + 0.005) * 100);
        return ((double) temp) / 100;
    }
    
    //!! refactor these conversions to "just do the math" (without string conversions)
    
    public static synchronized  Date barDate(long time) {
        //convert bar time to Date
        Date d = null;        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String s = "20" + Formats.fmtDate(time * 1000).substring(2,10);
            d = sdf.parse(s);
        } catch (Exception ex) {
            ex.printStackTrace();        
        }
        return d;
    }
    
    public static synchronized  Date barDateTime(long time) {
        //convert bar time to Date
        Date d = null;        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String s = "20" + Formats.fmtDate(time * 1000).substring(2,19);
            d = sdf.parse(s);
        } catch (Exception ex) {
            ex.printStackTrace();        
        }
        return d;
    }
   
    public static synchronized  LocalDateTime barLocalDateTime(long time) {
        //convert bartime to LocalDateTime
        Date d = barDateTime(time);  
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());   
    }     
    
    public static synchronized  LocalDate barLocalDate(long time) {
        return barLocalDateTime(time).toLocalDate();     
    }
    
    public static synchronized LocalTime barLocalTime(long time) {
        return barLocalDateTime(time).toLocalTime();
    }

    public static synchronized  long barTimeLong(LocalDateTime ldt) {
        //seconds from java epoch + seconds between TWS and java epochs
        return ldt.toEpochSecond(ZoneId.systemDefault().getRules().getOffset(ldt));
    }
    
    public static synchronized String csvBarLine(Bar bar) {        
        return logBar(bar) + "\n";
    }
    
    public static synchronized String logBar(Bar bar) {        
        DecimalFormat pf = new DecimalFormat("####.00");        
        DecimalFormat vf = new DecimalFormat("#########");        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        StringBuilder sb = new StringBuilder();
        LocalDateTime dt = AHutil.barLocalDateTime(bar.time());
        sb.append(dtf.format(dt)).append(",");        
        sb.append(pf.format(bar.open())).append(",");
        sb.append(pf.format(bar.high())).append(",");
        sb.append(pf.format(bar.low())).append(",");
        sb.append(pf.format(bar.close())).append(",");
        sb.append(vf.format(bar.volume())).append(",");
        sb.append(vf.format(bar.count())).append(",");
        sb.append(pf.format(bar.wap()));
        return sb.toString();
    }    

    //add an "R" last column to indicate that this is realtime data
    public static synchronized String csvRealtimeBarLine(Bar bar) {
        return AHutil.csvBarLine(bar).replace("\n", ",R\n");
    }
    
    public static synchronized String planToStr(double planId) {
        DecimalFormat df1 = new DecimalFormat("###");
        DecimalFormat df2 = new DecimalFormat("###");
        
        if (planId < 16 || planId > 17.99) {
            return df1.format(planId);
        } else if (planId >= 16.0 && planId < 16.99) {
            return df2.format(planId);
        } else if (planId >= 17.0 && planId < 17.99) {
            return df2.format(planId);
        } else if (planId == 10000) {
            return "Flip";
        }
        return "";
    }
    
    public static String tfString(boolean tf) {
        return tf ? "true" : "false";                
    }
    
    public static String UnixMillisecondsToString(long milliseconds, String dateFormat){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliseconds);
        return simpleDateFormat.format(calendar.getTime());
    }
    
    public static Bar createBar(String[] csvLine, boolean yearly) {
        String date = csvLine[0];
        long time;
        int year = Integer.parseInt(date.substring(0,4));
        int month = Integer.parseInt(date.substring(5,7));
        int day = Integer.parseInt(date.substring(8,10));
        
        if (yearly) {
            time  = new GregorianCalendar(year - 1900, month - 1, day).getTimeInMillis() / 1000;        
        } else {
            int hour = Integer.parseInt(date.substring(11,13));     //01234567890123456789
            int minute = Integer.parseInt(date.substring(14,16));   //yyyy-mm-dd hh:mm:ss
            int second = Integer.parseInt(date.substring(17,19));
            time = new GregorianCalendar(year - 1900, month - 1, day, hour, minute, second).getTimeInMillis() / 1000;                    
        }
        
        double open = Double.parseDouble(csvLine[1]);
        double high = Double.parseDouble(csvLine[2]);
        double low = Double.parseDouble(csvLine[3]);
        double close = Double.parseDouble(csvLine[4]);        
        long volume = Long.parseLong(csvLine[5]);
        return new Bar(time, high, low, open, close, 0, volume, 0);
    }    
    
}
