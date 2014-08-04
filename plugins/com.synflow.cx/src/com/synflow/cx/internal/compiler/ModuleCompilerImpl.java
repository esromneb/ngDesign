/*******************************************************************************
 * Copyright (c) 2012-2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cx.internal.compiler;

import static com.synflow.cx.CxConstants.NAME_LOOP;
import static com.synflow.cx.CxConstants.NAME_LOOP_DEPRECATED;
import static com.synflow.cx.CxConstants.NAME_SETUP;
import static com.synflow.cx.CxConstants.NAME_SETUP_DEPRECATED;
import static com.synflow.models.util.SwitchUtil.DONE;
import static com.synflow.models.util.SwitchUtil.visit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.generator.AbstractFileSystemAccess;
import org.eclipse.xtext.generator.IFileSystemAccess;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.synflow.core.transformations.ProcedureTransformation;
import com.synflow.core.transformations.SchedulerTransformation;
import com.synflow.core.transformations.impl.StoreOnceTransformation;
import com.synflow.cx.CxUtil;
import com.synflow.cx.cx.Bundle;
import com.synflow.cx.cx.Inst;
import com.synflow.cx.cx.Module;
import com.synflow.cx.cx.Network;
import com.synflow.cx.cx.Task;
import com.synflow.cx.cx.VarDecl;
import com.synflow.cx.cx.Variable;
import com.synflow.cx.cx.util.CxSwitch;
import com.synflow.cx.internal.compiler.helpers.FsmBeautifier;
import com.synflow.cx.internal.compiler.helpers.LoadStoreReplacer;
import com.synflow.cx.internal.compiler.helpers.SideEffectRemover;
import com.synflow.cx.internal.compiler.helpers.VariablePromoter;
import com.synflow.cx.internal.instantiation.IInstantiator;
import com.synflow.cx.internal.scheduler.CycleScheduler;
import com.synflow.cx.internal.scheduler.IfScheduler;
import com.synflow.cx.internal.services.Typer;
import com.synflow.models.dpn.Actor;
import com.synflow.models.dpn.DPN;
import com.synflow.models.dpn.Entity;
import com.synflow.models.dpn.Unit;
import com.synflow.models.util.Executable;
import com.synflow.models.util.Void;

/**
 * This class transforms a C~ module to IR entities (actors, networks, units).
 * 
 * @author Matthieu Wipliez
 * 
 */
@Singleton
public class ModuleCompilerImpl extends CxSwitch<Void> implements IModuleCompiler {

	private IFileSystemAccess fsa;

	@Inject
	private IInstantiator instantiator;

	@Inject
	private Typer typer;

	@Override
	public Void caseBundle(final Bundle bundle) {
		instantiator.forEachMapping(bundle, new Executable<Entity>() {
			@Override
			public void exec(Entity entity) {
				transformBundle(bundle, (Unit) entity);
				serialize(entity);
			}
		});
		return DONE;
	}

	@Override
	public Void caseInst(Inst inst) {
		return visit(this, inst.getTask());
	}

	@Override
	public Void caseModule(Module module) {
		// translate comments for this module
		new CommentTranslator(instantiator).doSwitch(module);

		return visit(this, module.getEntities());
	}

	@Override
	public Void caseNetwork(final Network network) {
		instantiator.forEachMapping(network, new Executable<Entity>() {
			@Override
			public void exec(Entity entity) {
				DPN dpn = (DPN) entity;
				for (Inst inst : network.getInstances()) {
					doSwitch(inst);
				}

				serialize(dpn);
			}
		});
		return DONE;
	}

	@Override
	public Void caseTask(final Task task) {
		instantiator.forEachMapping(task, new Executable<Entity>() {
			@Override
			public void exec(Entity entity) {
				transformTask(task, (Actor) entity);
				serialize(entity);
			}
		});
		return DONE;
	}

	@Override
	public void serialize(Entity entity) {
		// serializes to byte array (never throws exception)
		OutputStream os = new ByteArrayOutputStream();
		try {
			entity.eResource().save(os, null);
		} catch (IOException e) {
			// byte array output stream never throws exception
		}

		// serialize to relative file name (obtained by deresolving URI against base URI)
		URI base = ((AbstractFileSystemAccess) fsa).getURI("");
		URI uri = entity.eResource().getURI();
		String fileName = uri.deresolve(base).toString();
		fsa.generateFile(fileName, os.toString());
	}

	@Override
	public void serializeBuiltins() {
		for (Entity entity : instantiator.getBuiltins()) {
			serialize(entity);
		}
	}

	@Override
	public void setFileSystemAccess(IFileSystemAccess fsa) {
		this.fsa = fsa;
	}

	/**
	 * Transforms the given bundle into a unit.
	 * 
	 * @param bundle
	 *            Cx bundle
	 * @param unit
	 *            IR unit
	 */
	private void transformBundle(Bundle bundle, Unit unit) {
		transformDeclarations(unit, bundle.getDecls());
		new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(unit);
	}

	/**
	 * Transforms the given declarations (variables, procedures) to IR variables and procedures.
	 * 
	 * @param procedures
	 *            a list of IR procedures that will be created
	 * @param module
	 *            a list of declarations
	 */
	private void transformDeclarations(Entity entity, List<VarDecl> variables) {
		for (Variable variable : CxUtil.getStateVars(variables)) {
			if (CxUtil.isFunction(variable)) {
				if (CxUtil.isConstant(variable)) {
					// visit constant functions
					FunctionTransformer transformer = new FunctionTransformer(instantiator, typer,
							entity);
					transformer.doSwitch(variable);
				}
			} else {
				// visit variables (they are automatically added to the entity by mapper)
				instantiator.getMapping(variable);
			}
		}
	}

	/**
	 * Transforms the given task to an actor. Runs schedulers, transforms actor, beautifies FSM,
	 * runs several transformations on the code.
	 * 
	 * @param task
	 *            Cx task
	 * @param actor
	 *            IR actor
	 */
	private void transformTask(Task task, Actor actor) {
		transformDeclarations(actor, task.getDecls());

		// finds init and run functions
		Variable setup = null;
		Variable loop = null;
		for (Variable function : CxUtil.getFunctions(task.getDecls())) {
			String name = function.getName();
			if (NAME_SETUP.equals(name) || NAME_SETUP_DEPRECATED.equals(name)) {
				setup = function;
			} else if (NAME_LOOP.equals(name) || NAME_LOOP_DEPRECATED.equals(name)) {
				loop = function;
			}
		}

		// schedules cycles, if statements, and transforms actor
		CycleScheduler scheduler = new CycleScheduler(instantiator, actor);
		scheduler.schedule(setup, loop);
		new IfScheduler(instantiator, actor).visit();
		new ActorTransformer(instantiator, typer, actor).visit();

		// post-process FSM: rename states and actions
		new FsmBeautifier().visit(actor);

		// promotes local variables used over more than one cycle to state variables
		// and replaces load/stores of local variables by use/assigns
		new VariablePromoter(actor.getVariables()).visit(actor);
		new ProcedureTransformation(new LoadStoreReplacer()).doSwitch(actor);

		// apply store once transformation to scheduler and removes side effects
		new SchedulerTransformation(new StoreOnceTransformation()).doSwitch(actor);
		new SchedulerTransformation(new SideEffectRemover()).doSwitch(actor);
	}

}