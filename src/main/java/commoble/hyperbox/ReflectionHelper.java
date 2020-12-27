package commoble.hyperbox;

import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class ReflectionHelper
{

	// helper for making the private field getters via reflection
	@SuppressWarnings("unchecked") // also throws ClassCastException if the types are wrong
	public static <FIELDHOLDER,FIELDTYPE> Function<FIELDHOLDER,FIELDTYPE> getInstanceField(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
	{
		// forge's ORH is needed to reflect into vanilla minecraft java
		Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
		
		return instance -> {
			try
			{
				return (FIELDTYPE)(field.get(instance));
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				throw new RuntimeException(e);
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	public static <FIELDTYPE> Supplier<FIELDTYPE> getStaticField(Class<?> fieldHolderClass, String fieldName)
	{
		Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
		
		return () -> {
			try
			{
				return (FIELDTYPE) field.get(null);
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				throw new RuntimeException(e);
			}
		};
	}
	
	public static <FIELDTYPE> FIELDTYPE getStaticFieldOnce(Class<?> fieldHolderClass, String fieldName)
	{
		Supplier<FIELDTYPE> getter = getStaticField(fieldHolderClass, fieldName);
		return getter.get();
	}

}
