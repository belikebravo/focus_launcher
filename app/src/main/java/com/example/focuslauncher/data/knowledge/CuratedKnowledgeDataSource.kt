package com.example.focuslauncher.data.knowledge

object CuratedKnowledgeDataSource {
    
    fun getCuratedNuggets(): List<KnowledgeNugget> {
        return listOf(
            // OOP Concepts
            KnowledgeNugget(
                id = "curated_oop_1",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.BEGINNER,
                shortText = "Encapsulation",
                detailedText = "Encapsulation is like a capsule. It bundles data (variables) and methods (functions) together and restricts direct access to some of an object's components. This protects the internal state of the object."
            ),
            KnowledgeNugget(
                id = "curated_oop_2",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.BEGINNER,
                shortText = "Polymorphism",
                detailedText = "Polymorphism means 'many forms'. It allows objects of different classes to be treated as objects of a common superclass. For example, a 'Dog' and 'Cat' can both be treated as 'Animal', but they 'speak()' differently."
            ),
            KnowledgeNugget(
                id = "curated_oop_3",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.INTERMEDIATE,
                shortText = "Inheritance",
                detailedText = "Inheritance allows a new class to inherit properties and methods from an existing class. It promotes code reusability. Ideally, use it for 'is-a' relationships (e.g., a Car is a Vehicle)."
            ),
            KnowledgeNugget(
                id = "curated_oop_4",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.ADVANCED,
                shortText = "Composition over Inheritance",
                detailedText = "A design principle that suggests using instance variables that are references to other objects (Has-A) rather than inheriting from a class (Is-A). It offers more flexibility and cleaner code."
            ),
            KnowledgeNugget(
                id = "curated_oop_5",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.INTERMEDIATE,
                shortText = "Abstraction",
                detailedText = "Abstraction hides complex implementation details and shows only the necessary features of an object. Think of a car dashboard: you see the speedometer, not the engine combustion physics."
            ),
            KnowledgeNugget(
                id = "curated_oop_6",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.ADVANCED,
                shortText = "SOLID Principles",
                detailedText = "SOLID is an acronym for 5 design principles: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion. They help make software designs more understandable and maintainable."
            ),
             KnowledgeNugget(
                id = "curated_oop_7",
                topic = Topic("OOP", "Object Oriented Concepts"),
                difficulty = Difficulty.BEGINNER,
                shortText = "Class vs Object",
                detailedText = "A Class is a blueprint or template (like a cookie cutter). An Object is an instance created from that blueprint (like the cookie itself). You can create many objects from one class."
            ),
            
            // Productivity / Focus
            KnowledgeNugget(
                id = "curated_focus_1",
                topic = Topic("FOCUS", "Productivity"),
                difficulty = Difficulty.BEGINNER,
                shortText = "The Pomodoro Technique",
                detailedText = "A time management method where you work for 25 minutes, then take a 5-minute break. After 4 cycles, take a longer break. It helps maintain focus and prevents burnout."
            ),
            KnowledgeNugget(
                id = "curated_focus_2",
                topic = Topic("FOCUS", "Productivity"),
                difficulty = Difficulty.INTERMEDIATE,
                shortText = "Pareto Principle (80/20 Rule)",
                detailedText = "The idea that 80% of your results come from 20% of your efforts. Identify the few tasks that matter most and focus your energy on them."
            ),
            KnowledgeNugget(
                id = "curated_focus_3",
                topic = Topic("FOCUS", "Productivity"),
                difficulty = Difficulty.BEGINNER,
                shortText = "Deep Work",
                detailedText = "Professional activities performed in a state of distraction-free concentration that push your cognitive capabilities to their limit. This creates new value and is hard to replicate."
            )
        )
    }
}
