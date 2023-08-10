/*
  ============================================================================
  Name        : mqmon.java
  Author      : Zoff <zoff@zoff.cc>
  Version     :
  Copyright   : (C) 2021 - 2023 Zoff <zoff@zoff.cc>
  Description : simple listing of MQ queues and their depth
  ============================================================================
  */

/**
 * mqmon.java
 * Copyright (C) 2021 - 2023 Zoff <zoff@zoff.cc>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

//
// Dependencies:
//
// https://repo1.maven.org/maven2/com/ibm/mq/com.ibm.mq.allclient/9.2.4.0/com.ibm.mq.allclient-9.2.4.0.jar
// https://repo1.maven.org/maven2/org/json/json/20211205/json-20211205.jar
//
//
// Compile:
//
// javac -cp ./com.ibm.mq.allclient-9.2.4.0.jar mqmon.java
//
// Run:
//
// java -cp ./com.ibm.mq.allclient-9.2.4.0.jar:./json-20211205.jar:./ mqmon
//

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import com.ibm.mq.MQException;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;


public class mqmon {

  private static final String VERSION = "0.99.2";
  private static final String AUTHOR = "Zoff <zoff@zoff.cc>";

  private static Hashtable<String, Object> mqht;
  private static final SimpleDateFormat  LOGGER_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static final int depth_format_length = 6;

  public static void main(String[] args)
  {
    mqht = new Hashtable<String, Object>();
    // System.out.println("args: " + args.length);

    if ((args.length < 4) || (args.length > 5))
    {
        usage();
        System.exit(1);
    }
    else if ((args[0] == "--v") || (args[0] == "--version") || (args[0] == "-v"))
    {
        usage();
        System.exit(0);
    }

    int delta = 0;
    int MAX_QDEPTH = 1;
    if (args.length == 5)
    {
        delta = 1;
        MAX_QDEPTH = Integer.parseInt(args[0].substring(2));
        if (MAX_QDEPTH < 0)
        {
            MAX_QDEPTH = 1;
        }
    }

    String HOST = args[0 + delta]; // Host name or IP address
    int PORT = Integer.parseInt(args[1 + delta]); // Listener port for your queue manager
    String QMGR = args[2 + delta]; // Queue manager name
    String CHANNEL = args[3 + delta]; // Channel name
    // String APP_USER = "_USER_"; // User name that application uses to connect to MQ
    // String APP_PASSWORD = "_APP_PASSWORD_"; // Password that the application uses to connect to MQ

    mqht.put(CMQC.CHANNEL_PROPERTY, CHANNEL);
    mqht.put(CMQC.HOST_NAME_PROPERTY, HOST);
    mqht.put(CMQC.PORT_PROPERTY, new Integer(PORT));
    // mqht.put(CMQC.USER_ID_PROPERTY, APP_USER);
    // mqht.put(CMQC.PASSWORD_PROPERTY, APP_PASSWORD);

    MQQueueManager qMgr = null;
    PCFMessageAgent agent = null;
    PCFMessage   request = null;
    PCFMessage[] responses = null;

    try
    {
        qMgr = new MQQueueManager(QMGR, mqht);
    }
    catch(com.ibm.mq.MQException mqex)
    {
        System.out.println("MQException cc=" +mqex.completionCode + " : rc=" + mqex.reasonCode);
        System.exit(2);
    }

    try
    {
        agent = new PCFMessageAgent(qMgr);
        request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
        request.addParameter(CMQC.MQCA_Q_NAME, "*");

        // Add parameter to request only local queues
        request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL); // CMQC.MQQT_ALL does not work for some reason

		int[] attrs = {
                CMQC.MQCA_Q_NAME,
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_MAX_Q_DEPTH,
				CMQC.MQIA_MAX_MSG_LENGTH,
                CMQC.MQIA_Q_TYPE,
                CMQC.MQIA_OPEN_INPUT_COUNT,
				CMQC.MQIA_OPEN_OUTPUT_COUNT
                    };

        // Add parameter to request all of the attributes of the queue
        request.addParameter(CMQCFC.MQIACF_Q_ATTRS, attrs);

        if (MAX_QDEPTH > 0)
        {
            // only show queues with depth > MAX_QDEPTH except when param is zero
            request.addFilterParameter(CMQC.MQIA_CURRENT_Q_DEPTH, CMQCFC.MQCFOP_GREATER, (MAX_QDEPTH - 1));
        }

        responses = agent.send(request);

        for (int i = 0; i < responses.length; i++)
        {
            if ( ((responses[i]).getCompCode() == CMQC.MQCC_OK) && ((responses[i]).getParameterValue(CMQC.MQCA_Q_NAME) != null) )
            {
                String name = responses[i].getStringParameterValue(CMQC.MQCA_Q_NAME);
                if (name != null)
                {
                    name = name.trim();
                }

                int qtype = responses[i].getIntParameterValue(CMQC.MQIA_Q_TYPE);
                int depth = responses[i].getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                int maxDepth = responses[i].getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH);

                if ((depth > (MAX_QDEPTH - 1)) || (MAX_QDEPTH == 0))
                {
                    logger(String.format("%1$" + depth_format_length + "s", "" + depth) + "  " + name);
                }
            }
        }
    }
    catch (IOException e)
    {
        System.exit(3);
    }
    catch (MQDataException e)
    {
        System.exit(4);
    }
    finally
    {
        try
        {
            if (agent != null)
            {
               agent.disconnect();
            }
        }
        catch (MQDataException e)
        {
            System.exit(5);
        }

        try
        {
            if (qMgr != null)
            {
               qMgr.disconnect();
            }
        }
        catch (MQException e)
        {
            System.exit(6);
        }
    }
  }

  public static void usage()
  {
    System.out.println("Version: mqmon.java " + VERSION + " (C) 2021 - 2023 by " + AUTHOR);
    System.out.println("Usage  : mqmon   --version");
    System.out.println("         mqmon   HOST PORT QManager CHANNEL");
    System.out.println("         mqmon   -c<num> HOST PORT QManager CHANNEL");
  }

  public static void logger(String data)
  {
    System.out.println(data);
  }

}
