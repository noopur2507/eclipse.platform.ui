/*******************************************************************************
 * Copyright (c) 2004, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.progress;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.internal.progress.FinishedJobs.KeptJobsListener;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * The ProgressViewerContentProvider is the content provider progress viewers.
 */
public class ProgressViewerContentProvider extends ProgressContentProvider {
	protected AbstractProgressViewer progressViewer;

	private KeptJobsListener keptJobListener;

	private boolean showFinished;

	/**
	 * Create a new instance of the receiver.
	 *
	 * @param structured
	 *            The Viewer we are providing content for
	 * @param debug
	 *            If true debug information will be shown if the debug flag in
	 *            the ProgressManager is true.
	 * @param showFinished
	 *            A boolean that indicates whether or not the finished jobs
	 *            should be shown.
	 */
	public ProgressViewerContentProvider(AbstractProgressViewer structured,
			boolean debug, boolean showFinished) {
		super(debug);
		progressViewer = structured;
		this.showFinished = showFinished;
		if (showFinished) {
			FinishedJobs.getInstance().addListener(getKeptJobListener());
		}
	}

	/**
	 * Return a listener for kept jobs.
	 *
	 * @return KeptJobsListener
	 */
	private KeptJobsListener getKeptJobListener() {
		keptJobListener = new KeptJobsListener() {

			@Override
			public void finished(JobTreeElement jte) {
				final JobTreeElement element = jte;
				Job updateJob = new WorkbenchJob("Refresh finished") {//$NON-NLS-1$
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						refresh(new Object[] { element });
						return Status.OK_STATUS;
					}

					@Override
					public boolean shouldSchedule() {
						return !progressViewer.getControl().isDisposed();
					}

					@Override
					public boolean shouldRun() {
						return !progressViewer.getControl().isDisposed();
					}
				};
				updateJob.setSystem(true);
				updateJob.schedule();

			}

			@Override
			public void removed(JobTreeElement jte) {
				final JobTreeElement element = jte;
				Job updateJob = new WorkbenchJob("Remove finished") {//$NON-NLS-1$
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						if (element == null) {
							refresh();
						} else {
							ProgressViewerContentProvider.this.remove(new Object[] { element });
						}
						return Status.OK_STATUS;
					}
				};
				updateJob.setSystem(true);
				updateJob.schedule();

			}

		};
		return keptJobListener;
	}

	@Override
	public void refresh() {
		progressViewer.refresh(true);
	}

	@Override
	public void refresh(Object[] elements) {
		for (Object refresh : getRoots(elements, true)) {
			progressViewer.refresh(refresh, true);
		}
	}

	@Override
	public Object[] getElements(Object inputElement) {
		Object[] elements = super.getElements(inputElement);

		if (!showFinished)
			return elements;

		Set kept = FinishedJobs.getInstance().getKeptAsSet();

		if (kept.size() == 0)
			return elements;

		Set all = new HashSet();

		for (Object element : elements) {
			all.add(element);
		}

		Iterator keptIterator = kept.iterator();
		while (keptIterator.hasNext()) {
			JobTreeElement next = (JobTreeElement) keptIterator.next();
			if (next.getParent() != null && all.contains(next.getParent()))
				continue;
			all.add(next);

		}

		return all.toArray();
	}

	/**
	 * Get the root elements of the passed elements as we only show roots.
	 * Replace the element with its parent if subWithParent is true
	 *
	 * @param elements
	 *            the array of elements.
	 * @param subWithParent
	 *            sub with parent flag.
	 * @return Object[]
	 */
	private Object[] getRoots(Object[] elements, boolean subWithParent) {
		if (elements.length == 0) {
			return elements;
		}
		HashSet roots = new HashSet();
		for (Object element : elements) {
			JobTreeElement jobTreeElement = (JobTreeElement) element;
			if (jobTreeElement.isJobInfo()) {
				GroupInfo group = ((JobInfo) jobTreeElement).getGroupInfo();
				if (group == null) {
					roots.add(jobTreeElement);
				} else {
					if (subWithParent) {
						roots.add(group);
					}
				}
			} else {
				roots.add(jobTreeElement);
			}
		}
		return roots.toArray();
	}

	@Override
	public void add(Object[] elements) {
		progressViewer.add(elements);

	}

	@Override
	public void remove(Object[] elements) {
		progressViewer.remove(elements);

	}

	@Override
	public void dispose() {
		super.dispose();
		if (keptJobListener != null) {
			FinishedJobs.getInstance().removeListener(keptJobListener);
		}
	}
}
