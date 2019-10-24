package io.esense.test1;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import io.esense.esenselib.ESenseEvent;
import io.esense.esenselib.ESenseSensorListener;

public class ESenseSensorListenerImp implements ESenseSensorListener {

    private int data_id_counter;                                            // counts the number of times the sensors deliver information

    private short[] curr_accel, curr_gyro;
    //private String filename = "esense_out.txt";
    private String filepath = "EsenseLogs";
    private File output;
    private FileOutputStream outputStream;
    private Context ctx;

    @Override
    public void onSensorChanged(ESenseEvent evt) {
        curr_accel = evt.getAccel();
        curr_gyro = evt.getGyro();

        try {
            //outputStream = ctx.openFileOutput(output.getAbsolutePath(), Context.MODE_PRIVATE);
            //outputStream = new FileOutputStream (output.getAbsoluteFile().toString(), true);
            if (data_id_counter == 0) {
                outputStream.write(("time;accel_x;accel_y;accel_z;gyro_x;gyro_y;gyro_z" + System.getProperty("line.separator")).getBytes());
            }
            String content = evt.getTimestamp() + ";" + print_short_array(curr_accel) + ";" + print_short_array(curr_gyro) + System.getProperty("line.separator");
            //String content = data_id_counter + ";" + evt.getTimestamp() + ";" + print_short_array(curr_accel) + ";" + print_short_array(curr_gyro) + System.getProperty("line.separator");
            outputStream.write(content.getBytes());
            //outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        data_id_counter++;      // increase the sensor input counter
    }

    public void instantiate(File target_output, Context contxt) {
        output = target_output;
        ctx = contxt;
        data_id_counter = 0;

        try {
            outputStream = new FileOutputStream(output.getAbsoluteFile().toString(), false);    // Overwrite previous content of the file
            Log.d("SensorListener", "Output stream open.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close_output() {
        try {
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String print_short_array (short[] array) {
        String printed = "";
        for(int i=0; i<array.length; i++) {
            printed += array[i];
            if (i != array.length - 1) {
                printed += ";";
            }
        }

        return printed;
    }
}
