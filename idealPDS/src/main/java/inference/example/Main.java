/*******************************************************************************
 * Copyright (c) 2018 Fraunhofer IEM, Paderborn, Germany.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *  
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Johannes Spaeth - initial API and implementation
 *******************************************************************************/
package inference.example;

import boomerang.WeightedForwardQuery;
import boomerang.debugger.Debugger;
import boomerang.jimple.Statement;
import boomerang.jimple.Val;
import boomerang.results.ForwardBoomerangResults;
import com.google.common.base.Joiner;
import com.google.common.collect.Table;
import ideal.IDEALAnalysis;
import ideal.IDEALAnalysisDefinition;
import ideal.IDEALSeedSolver;
import inference.InferenceWeight;
import inference.InferenceWeightFunctions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.NewExpr;
import soot.options.Options;
import sync.pds.solver.WeightFunctions;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

public class Main {

	private static final Logger logger = LogManager.getLogger();

	public static void main(String... args) {
		String sootClassPath = System.getProperty("user.dir") + File.separator+"target"+File.separator+"classes";
		String mainClass = "inference.example.InferenceExample";
		setupSoot(sootClassPath, mainClass);
		analyze();
	}

	private static void setupSoot(String sootClassPath, String mainClass) {
		G.v().reset();
		Options.v().set_whole_program(true);
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().set_output_format(Options.output_format_none);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);

		List<String> includeList = new LinkedList<>();
		includeList.add("java.lang.*");
		includeList.add("java.util.*");
		includeList.add("java.io.*");
		includeList.add("sun.misc.*");
		includeList.add("java.net.*");
		includeList.add("javax.servlet.*");
		includeList.add("javax.crypto.*");

		Options.v().set_include(includeList);
		Options.v().setPhaseOption("jb", "use-original-names:true");

		Options.v().set_soot_classpath(sootClassPath);
		Options.v().set_prepend_classpath(true);

		Scene.v().loadNecessaryClasses();
		SootClass c = Scene.v().forceResolve(mainClass, SootClass.BODIES);
		if (c != null) {
			c.setApplicationClass();
			for (SootMethod m : c.getMethods()) {
				logger.debug(m);
			}
		}
	}
	private static void analyze() {
		Transform transform = new Transform("wjtp.ifds", createAnalysisTransformer());
		PackManager.v().getPack("wjtp").add(transform);
		PackManager.v().getPack("cg").apply();
		PackManager.v().getPack("wjtp").apply();
	}

	private static Transformer createAnalysisTransformer() {
		return new SceneTransformer() {
			protected void internalTransform(String phaseName, @SuppressWarnings("rawtypes") Map options) {
				
				IDEALAnalysis<InferenceWeight> solver = new IDEALAnalysis<>(new IDEALAnalysisDefinition<InferenceWeight>() {

					@Override
					public Collection<WeightedForwardQuery<InferenceWeight>> generate(SootMethod method, Unit stmt, Collection<SootMethod> calledMethod) {
						if(stmt instanceof AssignStmt){
							AssignStmt as = (AssignStmt) stmt;
							if(as.getRightOp() instanceof NewExpr &&
									as.getRightOp().getType().toString().contains("inference.example.InferenceExample$File")){
								return Collections.singleton(new WeightedForwardQuery<InferenceWeight>(new Statement(as, method), new Val(as.getLeftOp(), method), InferenceWeight.one()));
							}
						}
						return Collections.emptySet();
					}

					@Override
					public WeightFunctions<Statement, Val, Statement, InferenceWeight> weightFunctions() {
						return new InferenceWeightFunctions();
					}
					
					@Override
					public Debugger<InferenceWeight> debugger(IDEALSeedSolver<InferenceWeight> solver) {
						return new Debugger<>();
					}
				});
				
				Map<WeightedForwardQuery<InferenceWeight>, ForwardBoomerangResults<InferenceWeight>> res = solver.run();
				for(Entry<WeightedForwardQuery<InferenceWeight>, ForwardBoomerangResults<InferenceWeight>> e : res.entrySet()){
					Table<Statement, Val, InferenceWeight> results = e.getValue().asStatementValWeightTable();
					logger.info(Joiner.on("\n").join(results.cellSet()));
				}
			}
		};
	}

}
