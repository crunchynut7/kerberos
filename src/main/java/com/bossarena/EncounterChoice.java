package com.bossarena;

import com.bossarena.encounters.KerberosEncounter;
import com.bossarena.encounters.SparringGolemEncounter;
import com.bossarena.engine.Encounter;
import java.util.function.Supplier;

/** The fights the plugin can start, as shown in the config dropdown. */
public enum EncounterChoice
{
	KERBEROS("Kerberos", KerberosEncounter::new),
	SPARRING_GOLEM("Sparring Golem", SparringGolemEncounter::new);

	private final String label;
	private final Supplier<Encounter> factory;

	EncounterChoice(String label, Supplier<Encounter> factory)
	{
		this.label = label;
		this.factory = factory;
	}

	/** A fresh encounter for one attempt. */
	public Encounter create()
	{
		return factory.get();
	}

	@Override
	public String toString()
	{
		return label;
	}
}
