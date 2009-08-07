/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong> for processing the contents of a
 * Cluster definition element.  </p>
 *
 * @author Filip Hanik
 * @version $Revision$ $Date$
 */

public class ClusterRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected String prefix = null;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public ClusterRuleSet() {

        this("");

    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public ClusterRuleSet(String prefix) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add the set of Rule instances defined in this RuleSet to the
     * specified <code>Digester</code> instance, associating them with
     * our namespace URI (if any).  This method should only be called
     * by a Digester instance.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    public void addRuleInstances(Digester digester) {
        //Cluster configuration start
        digester.addObjectCreate(prefix + "Membership",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Membership");
        digester.addSetNext(prefix + "Membership",
                            "setMembershipService",
                            "org.apache.catalina.cluster.MembershipService");
        
        digester.addObjectCreate(prefix + "Sender",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Sender");
        digester.addSetNext(prefix + "Sender",
                            "setClusterSender",
                            "org.apache.catalina.cluster.ClusterSender");

        digester.addObjectCreate(prefix + "Receiver",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Receiver");
        digester.addSetNext(prefix + "Receiver",
                            "setClusterReceiver",
                            "org.apache.catalina.cluster.ClusterReceiver");

        digester.addObjectCreate(prefix + "Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Valve");
        digester.addSetNext(prefix + "Valve",
                            "addValve",
                            "org.apache.catalina.Valve");
        
        digester.addObjectCreate(prefix + "Deployer",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Deployer");
        digester.addSetNext(prefix + "Deployer",
                            "setClusterDeployer",
                            "org.apache.catalina.cluster.ClusterDeployer");



        //Cluster configuration end
    }


}
